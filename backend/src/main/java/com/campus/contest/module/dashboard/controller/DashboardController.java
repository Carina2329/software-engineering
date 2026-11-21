package com.campus.contest.module.dashboard.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.dashboard.service.DashboardOverviewService;
import com.campus.contest.module.dashboard.service.DashboardTicketService;
import com.campus.contest.module.dashboard.vo.DashboardOverviewVO;
import com.campus.contest.module.dashboard.vo.DashboardTicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardOverviewService overviewService;
    private final DashboardTicketService ticketService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Result<DashboardOverviewVO> overview() {
        return Result.ok(overviewService.getCurrentOverview());
    }

    @PostMapping("/ws-ticket")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public Result<DashboardTicketVO> createWebSocketTicket() {
        return Result.ok(ticketService.createCurrentTicket());
    }
}
