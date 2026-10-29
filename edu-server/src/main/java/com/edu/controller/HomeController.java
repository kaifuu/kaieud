package com.edu.controller;

import com.edu.common.Result;
import com.edu.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 首页概览 */
@RestController
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/home/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(homeService.overview());
    }
}
