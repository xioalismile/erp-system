package com.erp.print.template.service;

import com.lowagie.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * 打印模板 HTML → PDF（2026-11-11 手机端打印）
 *
 * <p>为什么需要：微信小程序没有打印接口，手机内嵌浏览器也不支持 window.print()，
 * 手机端唯一可行的“打印/另存 PDF”路径是服务端先生成 PDF，再由小程序原生 PDF 阅读器打开
 * （wx.openDocument，右上角可转发/保存/用其他应用打开）。</p>
 *
 * <p>渲染口径：仍用「设置中心 → 打印模板」里配置的 HTML，不另做一套排版，
 * 保证电脑端与手机端出自同一份模板。差异说明：
 * ① flying-saucer 支持 CSS 2.1 子集，flex/grid 与伪元素水印不生效（水印会丢）；
 * ② 为保证中文不乱码，统一强制使用服务器中文字体（覆盖模板里的 font-family）。</p>
 */
@Slf4j
@Service
public class PrintPdfService {

    /** PDF 内统一使用的中文字体族（按顺序回退；实测“覆盖族名”那种注册方式不生效，必须用字体自身族名） */
    private static final String CJK_FAMILY_FALLBACK = "SimHei, Microsoft YaHei, SimSun, KaiTi, WenQuanYi Zen Hei, sans-serif";

    /**
     * 中文字体候选：{字体文件, 字体自身族名}
     * 注意族名必须与字体文件内的真实名称一致 —— flying-saucer 按 CSS font-family 精确匹配，
     * 用 addFont 的“族名覆盖”参数实测无效（中文会被整段丢弃，PDF 里只剩数字与英文）。
     */
    private static final String[][] FONT_CANDIDATES = {
            {"C:/Windows/Fonts/simhei.ttf", "SimHei"},
            {"C:/Windows/Fonts/msyh.ttc", "Microsoft YaHei"},
            {"C:/Windows/Fonts/simsun.ttc", "SimSun"},
            {"C:/Windows/Fonts/simkai.ttf", "KaiTi"},
            {"C:/Windows/Fonts/msyhbd.ttc", "Microsoft YaHei"},
            {"/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", "WenQuanYi Zen Hei"},
            {"/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", "Noto Sans CJK SC"}
    };

    /**
     * 把打印模板 HTML 渲染成 PDF 字节流
     *
     * @param html    打印模板渲染出来的完整 HTML（含 head/style）
     * @param baseUrl 站内相对资源（如 /uploads/xx.png）的基地址，可为空
     * @return PDF 字节
     */
    public byte[] render(String html, String baseUrl) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("打印模板没有返回内容，请先在电脑端配置该单据的默认打印模板");
        }
        ITextRenderer renderer = new ITextRenderer();
        String family = applyCjkFont(renderer);
        String xhtml = toXhtml(html, family);
        try {
            renderer.setDocumentFromString(xhtml, baseUrl == null ? "" : baseUrl);
            renderer.layout();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            renderer.createPDF(out, true);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("打印模板转 PDF 失败", e);
            throw new RuntimeException("打印模板转 PDF 失败：" + e.getMessage(), e);
        }
    }

    /**
     * HTML → 合法 XHTML：flying-saucer 用 XML 解析器读文档，
     * 模板里的 &lt;br&gt;、&amp;nbsp;、未闭合标签都会直接抛解析异常，必须先规范化。
     */
    private String toXhtml(String html, String family) {
        Document doc = Jsoup.parse(html);
        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset("UTF-8");
        // 统一中文字体：模板里写的 'Microsoft YaHei' 等在服务器上不一定存在，缺字会直接整段丢失
        Element head = doc.head();
        if (head != null) {
            Element style = doc.createElement("style");
            style.appendText("body, div, span, p, td, th, table, tr, h1, h2, h3, h4, h5, h6, label { font-family: "
                    + family + "; }");
            head.appendChild(style);
        }
        return doc.outerHtml();
    }

    /**
     * 注册服务器上所有可用的中文字体，并返回给 CSS 用的字体族列表（按候选顺序回退）。
     * 一个都没找到时仍然返回默认列表（此时中文会缺字，日志里会有告警）。
     */
    private String applyCjkFont(ITextRenderer renderer) {
        ITextFontResolver resolver = renderer.getFontResolver();
        java.util.List<String> families = new java.util.ArrayList<>();
        for (String[] candidate : FONT_CANDIDATES) {
            String path = candidate[0];
            String family = candidate[1];
            try {
                if (!new File(path).isFile()) continue;
                resolver.addFont(fontLoadName(path), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                if (!families.contains(family)) families.add(family);
            } catch (Exception e) {
                log.warn("注册中文字体失败（跳过）：{}，{}", path, e.getMessage());
            }
        }
        if (families.isEmpty()) {
            log.warn("未找到可用的中文字体文件，打印 PDF 中的中文会缺失；候选：{}", FONT_CANDIDATES.length);
            return CJK_FAMILY_FALLBACK;
        }
        log.debug("打印 PDF 已注册中文字体：{}", families);
        // 已注册的放最前，其余候选保留在列表里做兜底
        StringBuilder sb = new StringBuilder();
        for (String f : families) sb.append("'").append(f).append("', ");
        return sb.append(CJK_FAMILY_FALLBACK).toString();
    }

    /** TTC 字体集合必须带索引（如 simsun.ttc,0），TTF 直接用路径 */
    private String fontLoadName(String path) {
        return path.toLowerCase().endsWith(".ttc") ? path + ",0" : path;
    }
}
