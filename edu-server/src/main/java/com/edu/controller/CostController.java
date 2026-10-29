package com.edu.controller;

import com.edu.common.Result;
import com.edu.mapper.CostMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 成本毛利报表（仅校长/财务可见，教务无此权限）。
 * 统计口径：以消课时间统计（不是缴费时间）。
 */
@RestController
@RequestMapping("/cost")
@RequiredArgsConstructor
@RequiresPerm("edu:cost")
public class CostController {

    private final CostMapper costMapper;

    @GetMapping("/detail")
    public Result<List<Map<String, Object>>> detail(@RequestParam(required = false) String begin,
                                                    @RequestParam(required = false) String end,
                                                    @RequestParam(required = false) Long teacherId,
                                                    @RequestParam(required = false) Long courseId,
                                                    @RequestParam(required = false) Integer classType) {
        return Result.ok(costMapper.detail(tenantId(), begin, end, teacherId, courseId, classType));
    }

    @GetMapping("/summary")
    public Result<List<Map<String, Object>>> summary(@RequestParam(defaultValue = "teacher") String groupBy,
                                                     @RequestParam(required = false) String begin,
                                                     @RequestParam(required = false) String end) {
        return Result.ok(costMapper.summary(tenantId(), begin, end, groupBy, null, null, null));
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(@RequestParam(required = false) String begin,
                                                 @RequestParam(required = false) String end) {
        return Result.ok(costMapper.dashboard(tenantId(), begin, end, null, null, null));
    }

    private Long tenantId() {
        Long t = AuthContext.get().getTenantId();
        return t != null && t == 0 ? null : t; // 超管查看全部
    }
}
