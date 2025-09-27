package com.sharenote.redistribution.controller;

import com.sharenote.redistribution.dto.response.MigrationProgressInfo;
import com.sharenote.redistribution.service.migration.MigrationProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationProgressController {
    private final MigrationProgressService migrationProgressService;

    @GetMapping("/progress")
    public ResponseEntity<MigrationProgressInfo> getMigrationProgress() {
        MigrationProgressInfo progressInfo = migrationProgressService.getCurrentProgress();
        return ResponseEntity.ok(progressInfo);
    }

    @GetMapping("/percentage")
    public ResponseEntity<Double> getMigrationProgressPercentage() {
        double percentage = migrationProgressService.getMigrationProgressPercentage();
        return ResponseEntity.ok(percentage);
    }
}
