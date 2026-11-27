package com.erp.finance.expense.type.controller;

import com.erp.common.PageResult;
import com.erp.common.Result;
import com.erp.finance.expense.type.entity.ExpenseType;
import com.erp.finance.expense.type.service.ExpenseTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/expense-type")
@RequiredArgsConstructor
public class ExpenseTypeController {

    private final ExpenseTypeService expenseTypeService;

    @GetMapping
    public Result<PageResult<ExpenseType>> pageList(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String expenseCategory,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(expenseTypeService.pageQuery(current, size, keyword, expenseCategory, status, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public Result<ExpenseType> getDetail(@PathVariable Long id) {
        return Result.success(expenseTypeService.getDetail(id));
    }

    @GetMapping("/list/{category}")
    public Result<List<ExpenseType>> listByCategory(@PathVariable String category) {
        return Result.success(expenseTypeService.listByCategory(category));
    }

    @PostMapping
    public Result<ExpenseType> save(@RequestBody ExpenseType expenseType) {
        return Result.success(expenseTypeService.saveExpenseType(expenseType));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        expenseTypeService.delete(id);
        return Result.success();
    }
}
