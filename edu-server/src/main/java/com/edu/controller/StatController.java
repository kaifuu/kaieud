package com.edu.controller;

import com.edu.common.Result;
import com.edu.security.RequiresPerm;
import com.edu.service.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** 统计汇总：学校/教师/学生维度收入、课时占比、教师投入量与闲置量 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
@RequiresPerm("edu:stats")
public class StatController {

    private final StatService statService;

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) String begin,
                                               @RequestParam(required = false) String end) {
        if (begin == null) begin = LocalDate.now().withDayOfYear(1).toString();
        if (end == null) end = LocalDate.now().plusDays(1).toString();
        return Result.ok(statService.summary(begin, end));
    }
}
