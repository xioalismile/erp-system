package com.erp.board.overview;

import com.erp.common.Result;
import com.erp.common.scope.DataScopeResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据看板 · 经营链路接口
 *
 * 该控制器位于定制源码目录 backend/src/custom/<服务器编码>/java/，
 * 只有构建时激活同名 profile 才会被编译进包；未激活的服务器产物内不存在该接口。
 */
@RestController
@RequestMapping("/api/board/overview")
@RequiredArgsConstructor
public class OverviewFlowController {

    private final OverviewFlowService overviewFlowService;
    private final DataScopeResolver dataScopeResolver;

    @GetMapping("/flow")
    public Result<Map<String, Object>> flow(@RequestParam(defaultValue = "month") String period,
                                            HttpServletRequest request) {
        boolean allScope = dataScopeResolver.resolve(request).isAll();
        return Result.success(overviewFlowService.flow(period, allScope));
    }
}
