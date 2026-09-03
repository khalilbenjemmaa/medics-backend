package com.reemamiri.practice.admin.controller;

import com.reemamiri.practice.admin.dto.DashboardResponse;
import com.reemamiri.practice.admin.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin dashboard")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Overview counts, today's list and what is coming up")
    @GetMapping
    public DashboardResponse overview() {
        return dashboardService.overview();
    }
}
