package com.erp.common.search;

import com.erp.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 全局搜索（万能搜索框）接口。
 *
 * <p>位于 /api/** 鉴权拦截范围内（需登录）；单据结果按登录人数据权限过滤（见 GlobalSearchService）。</p>
 */
@RestController
@RequestMapping("/api/common/global-search")
@RequiredArgsConstructor
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    /** 按单号搜单据（精确命中优先 + 模糊包含），供页头万能搜索框调用 */
    @GetMapping("/bills")
    public Result<List<Map<String, Object>>> bills(@RequestParam(required = false) String keyword,
                                                   HttpServletRequest request) {
        return Result.success(globalSearchService.searchBills(keyword, request));
    }
}
