package com.campus.contest.module.system.controller;

import com.campus.contest.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 脚手架联调测试接口：前后端打通验证用，正式开发后可删除。
 */
@Tag(name = "脚手架测试")
@RestController
@RequestMapping("/api/v1/test")
public class TestController {

    @Operation(summary = "连通性测试")
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("pong");
    }
}
