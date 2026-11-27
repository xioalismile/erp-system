package com.erp.report.custom.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义报表公式引擎（只生成 SQL 片段，不做任何代码执行）。
 *
 * <p>支持的写法：
 * <ul>
 *   <li>字段引用：[source.field]（明细模式）或 [聚合输出名]（汇总模式）</li>
 *   <li>数字：12、0.13</li>
 *   <li>四则运算：+ - * /，支持括号</li>
 *   <li>函数：ROUND(x,n) / ABS(x) / COALESCE(x,y) / NULLIF(x,y) /
 *       TOTAL(x)（汇总模式下 = 占全体合计，生成 SUM(x) OVER ()）</li>
 * </ul>
 *
 * <p>安全：字段引用由调用方（报表 SQL 构建器）按白名单解析成列名，数字过正则，
 * 函数名过白名单，其余字符一律拒绝；用户输入不直接拼进 SQL。
 */
public final class FormulaEngine {

    private FormulaEngine() {
    }

    /** 字段解析器：把公式里的字段引用翻译成 SQL 表达式 */
    public interface FieldResolver {
        Resolved resolve(String ref);
    }

    /** 字段解析结果：SQL 表达式 + 字段类型（amount/number/date/...） */
    public static class Resolved {
        public final String sql;
        public final String type;

        public Resolved(String sql, String type) {
            this.sql = sql;
            this.type = type;
        }
    }

    /** 公式编译结果 */
    public static class Result {
        public final String sql;
        public final String type;

        public Result(String sql, String type) {
            this.sql = sql;
            this.type = type;
        }
    }

    /**
     * 编译公式。
     *
     * @param formula    公式文本
     * @param resolver   字段解析器
     * @param allowTotal 是否允许 TOTAL()（仅汇总模式）
     */
    public static Result compile(String formula, FieldResolver resolver, boolean allowTotal) {
        if (formula == null || formula.trim().isEmpty()) {
            throw new IllegalArgumentException("公式不能为空");
        }
        Parser p = new Parser(tokenize(formula), resolver, allowTotal);
        Node n = p.parseExpr();
        p.expectEnd();
        String sql = n.sql;
        String type = n.amount && !n.divided ? "amount" : "number";
        return new Result(sql, type);
    }

    // ==================== 词法 ====================

    private enum Kind { NUM, REF, FUNC, OP, LP, RP, COMMA }

    private static class Token {
        final Kind kind;
        final String text;

        Token(Kind kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    private static final java.util.Set<String> FUNCS =
            new java.util.HashSet<>(java.util.Arrays.asList("ROUND", "ABS", "COALESCE", "NULLIF", "TOTAL"));

    private static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '[') {
                int end = s.indexOf(']', i + 1);
                if (end < 0) throw new IllegalArgumentException("公式缺少右中括号: " + s.substring(i));
                String ref = s.substring(i + 1, end).trim();
                if (ref.isEmpty()) throw new IllegalArgumentException("公式存在空字段引用");
                out.add(new Token(Kind.REF, ref));
                i = end + 1;
                continue;
            }
            if (Character.isDigit(c) || c == '.') {
                int start = i;
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                String num = s.substring(start, i);
                if (!num.matches("\\d+(\\.\\d+)?")) {
                    throw new IllegalArgumentException("公式数字格式不正确: " + num);
                }
                out.add(new Token(Kind.NUM, num));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                String name = s.substring(start, i).toUpperCase();
                if (!FUNCS.contains(name)) {
                    throw new IllegalArgumentException("公式函数不支持: " + name
                            + "（可用 ROUND/ABS/COALESCE/NULLIF/TOTAL）");
                }
                out.add(new Token(Kind.FUNC, name));
                continue;
            }
            if (c == '+' || c == '-' || c == '*' || c == '/') {
                out.add(new Token(Kind.OP, String.valueOf(c)));
                i++;
                continue;
            }
            if (c == '(') {
                out.add(new Token(Kind.LP, "("));
                i++;
                continue;
            }
            if (c == ')') {
                out.add(new Token(Kind.RP, ")"));
                i++;
                continue;
            }
            if (c == ',') {
                out.add(new Token(Kind.COMMA, ","));
                i++;
                continue;
            }
            throw new IllegalArgumentException("公式含不支持的字符: " + c);
        }
        return out;
    }

    // ==================== 语法 ====================

    private static class Node {
        final String sql;
        final boolean amount;
        final boolean divided;

        Node(String sql, boolean amount, boolean divided) {
            this.sql = sql;
            this.amount = amount;
            this.divided = divided;
        }
    }

    private static class Parser {
        private final List<Token> tokens;
        private final FieldResolver resolver;
        private final boolean allowTotal;
        private int pos = 0;

        Parser(List<Token> tokens, FieldResolver resolver, boolean allowTotal) {
            this.tokens = tokens;
            this.resolver = resolver;
            this.allowTotal = allowTotal;
        }

        Token peek() {
            return pos < tokens.size() ? tokens.get(pos) : null;
        }

        Token next() {
            if (pos >= tokens.size()) throw new IllegalArgumentException("公式不完整");
            return tokens.get(pos++);
        }

        void expectEnd() {
            if (pos != tokens.size()) {
                throw new IllegalArgumentException("公式存在多余内容: " + tokens.get(pos).text);
            }
        }

        /** expr := term (('+'|'-') term)* */
        Node parseExpr() {
            Node left = parseTerm();
            while (true) {
                Token t = peek();
                if (t != null && t.kind == Kind.OP && (t.text.equals("+") || t.text.equals("-"))) {
                    next();
                    Node right = parseTerm();
                    left = new Node("(" + left.sql + " " + t.text + " " + right.sql + ")",
                            left.amount && right.amount, left.divided || right.divided);
                } else {
                    return left;
                }
            }
        }

        /** term := factor (('*'|'/') factor)* */
        Node parseTerm() {
            Node left = parseFactor();
            while (true) {
                Token t = peek();
                if (t != null && t.kind == Kind.OP && (t.text.equals("*") || t.text.equals("/"))) {
                    next();
                    Node right = parseFactor();
                    String sql = t.text.equals("/")
                            ? "(" + left.sql + " / NULLIF(" + right.sql + ", 0))"
                            : "(" + left.sql + " * " + right.sql + ")";
                    left = new Node(sql, left.amount && right.amount, left.divided || right.divided || t.text.equals("/"));
                } else {
                    return left;
                }
            }
        }

        /** factor := ('-'|'+')? (数字 | 字段 | 函数(...) | '(' expr ')') */
        Node parseFactor() {
            Token t = peek();
            if (t == null) throw new IllegalArgumentException("公式不完整");
            if (t.kind == Kind.OP && (t.text.equals("-") || t.text.equals("+"))) {
                next();
                Node inner = parseFactor();
                return t.text.equals("-")
                        ? new Node("(-" + inner.sql + ")", inner.amount, inner.divided)
                        : inner;
            }
            if (t.kind == Kind.NUM) {
                next();
                return new Node(t.text, false, false);
            }
            if (t.kind == Kind.REF) {
                next();
                Resolved r = resolver.resolve(t.text);
                boolean amount = "amount".equals(r.type);
                return new Node(r.sql, amount, false);
            }
            if (t.kind == Kind.LP) {
                next();
                Node inner = parseExpr();
                Token rp = next();
                if (rp.kind != Kind.RP) throw new IllegalArgumentException("公式括号不匹配");
                return new Node("(" + inner.sql + ")", inner.amount, inner.divided);
            }
            if (t.kind == Kind.FUNC) {
                next();
                return parseFunc(t.text);
            }
            throw new IllegalArgumentException("公式写法不正确: " + t.text);
        }

        Node parseFunc(String name) {
            Token lp = next();
            if (lp.kind != Kind.LP) throw new IllegalArgumentException("函数 " + name + " 缺少左括号");
            List<Node> args = new ArrayList<>();
            if (peek() != null && peek().kind == Kind.RP) {
                next();
            } else {
                while (true) {
                    args.add(parseExpr());
                    Token sep = next();
                    if (sep.kind == Kind.COMMA) continue;
                    if (sep.kind == Kind.RP) break;
                    throw new IllegalArgumentException("函数 " + name + " 参数写法不正确");
                }
            }
            switch (name) {
                case "ROUND": {
                    requireArgs(name, args, 2);
                    if (!args.get(1).sql.matches("\\d+")) {
                        throw new IllegalArgumentException("ROUND 的第二个参数必须是整数");
                    }
                    return new Node("ROUND(" + args.get(0).sql + ", " + args.get(1).sql + ")",
                            args.get(0).amount, args.get(0).divided);
                }
                case "ABS": {
                    requireArgs(name, args, 1);
                    return new Node("ABS(" + args.get(0).sql + ")", args.get(0).amount, args.get(0).divided);
                }
                case "COALESCE": {
                    requireArgs(name, args, 2);
                    return new Node("COALESCE(" + args.get(0).sql + ", " + args.get(1).sql + ")",
                            args.get(0).amount && args.get(1).amount,
                            args.get(0).divided || args.get(1).divided);
                }
                case "NULLIF": {
                    requireArgs(name, args, 2);
                    return new Node("NULLIF(" + args.get(0).sql + ", " + args.get(1).sql + ")",
                            args.get(0).amount, args.get(0).divided);
                }
                case "TOTAL": {
                    requireArgs(name, args, 1);
                    if (!allowTotal) {
                        throw new IllegalArgumentException("TOTAL() 仅汇总模式可用");
                    }
                    return new Node("SUM(" + args.get(0).sql + ") OVER ()", true, args.get(0).divided);
                }
                default:
                    throw new IllegalArgumentException("公式函数不支持: " + name);
            }
        }

        private void requireArgs(String name, List<Node> args, int n) {
            if (args.size() != n) {
                throw new IllegalArgumentException("函数 " + name + " 需要 " + n + " 个参数");
            }
        }
    }
}
