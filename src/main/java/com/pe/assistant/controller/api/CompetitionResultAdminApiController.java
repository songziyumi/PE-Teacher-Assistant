package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.CompetitionResultService;
import com.pe.assistant.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/competition")
@RequiredArgsConstructor
public class CompetitionResultAdminApiController {

    private final CompetitionResultService competitionResultService;
    private final CurrentUserService currentUserService;

    @GetMapping("/{competitionId}/results")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @PathVariable Long competitionId,
            @RequestParam(required = false) Long eventId) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            return ResponseEntity.ok(ApiResponse.ok(competitionResultService.listResults(teacher, competitionId, eventId).stream()
                    .map(competitionResultService::toMap)
                    .toList()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/{competitionId}/results")
    public ResponseEntity<ApiResponse<Map<String, Object>>> save(
            @PathVariable Long competitionId,
            @RequestBody Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            return ResponseEntity.ok(ApiResponse.ok(competitionResultService.toMap(
                    competitionResultService.saveResult(teacher, competitionId, body))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/{competitionId}/results/batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveBatch(
            @PathVariable Long competitionId,
            @RequestBody List<Map<String, Object>> items) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            int saved = competitionResultService.saveBatch(teacher, competitionId, items);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("savedCount", saved)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PutMapping("/{competitionId}/results/publish")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publish(
            @PathVariable Long competitionId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            Long eventId = body != null && body.get("eventId") != null ? Long.valueOf(String.valueOf(body.get("eventId"))) : null;
            int count = competitionResultService.publish(teacher, competitionId, eventId);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("publishedCount", count)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}