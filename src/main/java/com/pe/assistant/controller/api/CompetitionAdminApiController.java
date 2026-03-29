package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.entity.Competition;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.CompetitionEventService;
import com.pe.assistant.service.CompetitionService;
import com.pe.assistant.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/competition")
@RequiredArgsConstructor
public class CompetitionAdminApiController {

    private final CompetitionService competitionService;
    private final CompetitionEventService competitionEventService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        Teacher teacher = currentUserService.getCurrentTeacher();
        return ApiResponse.ok(competitionService.listVisible(teacher).stream().map(competitionService::toMap).toList());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            Competition competition = competitionService.create(teacher, body);
            return ResponseEntity.ok(ApiResponse.ok(competitionService.toMap(competition)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/{competitionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable Long competitionId) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            Competition competition = competitionService.requireVisible(teacher, competitionId);
            return ResponseEntity.ok(ApiResponse.ok(competitionService.toMap(competition)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PutMapping("/{competitionId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStatus(
            @PathVariable Long competitionId,
            @RequestBody Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            String targetStatus = String.valueOf(body.get("targetStatus"));
            Competition competition = competitionService.updateStatus(teacher, competitionId, targetStatus);
            return ResponseEntity.ok(ApiResponse.ok(competitionService.toMap(competition)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/{competitionId}/events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listEvents(@PathVariable Long competitionId) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            Competition competition = competitionService.requireVisible(teacher, competitionId);
            return ResponseEntity.ok(ApiResponse.ok(
                    competitionEventService.listByCompetition(competition.getId()).stream()
                            .map(competitionEventService::toMap)
                            .toList()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/{competitionId}/events")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createEvent(
            @PathVariable Long competitionId,
            @RequestBody Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            Competition competition = competitionService.requireHostManaged(teacher, competitionId);
            return ResponseEntity.ok(ApiResponse.ok(competitionEventService.toMap(
                    competitionEventService.create(competition, body))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
