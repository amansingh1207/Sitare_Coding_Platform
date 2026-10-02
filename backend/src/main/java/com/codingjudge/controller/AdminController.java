package com.codingjudge.controller;

import com.codingjudge.model.dto.ApiResponse;
import com.codingjudge.model.dto.response.AdminProblemSummary;
import com.codingjudge.model.dto.response.ImportPackResponse;
import com.codingjudge.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/problems")
    public ResponseEntity<ApiResponse<List<AdminProblemSummary>>> listProblems() {
        List<AdminProblemSummary> problems = adminService.listProblems();
        return ResponseEntity.ok(ApiResponse.ok(problems));
    }

    @PostMapping("/problems/import-pack")
    public ResponseEntity<ApiResponse<ImportPackResponse>> importProblemPack(
            @RequestParam("weekLabel") String weekLabel,
            @RequestParam(value = "defaultTimeLimitMs", defaultValue = "2000") Integer defaultTimeLimitMs,
            @RequestParam(value = "defaultMemoryLimitMb", defaultValue = "256") Integer defaultMemoryLimitMb,
            @RequestParam(value = "defaultDifficulty", defaultValue = "MEDIUM") String defaultDifficulty,
            @RequestPart("file") MultipartFile file) {
        ImportPackResponse result = adminService.importProblemPack(
                file, weekLabel, defaultTimeLimitMs, defaultMemoryLimitMb, defaultDifficulty);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
