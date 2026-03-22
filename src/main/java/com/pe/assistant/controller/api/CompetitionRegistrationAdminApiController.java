package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.entity.CompetitionRegistration;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.CompetitionRegistrationItemService;
import com.pe.assistant.service.CompetitionRegistrationService;
import com.pe.assistant.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/competition")
@RequiredArgsConstructor
public class CompetitionRegistrationAdminApiController {

    private final CompetitionRegistrationService registrationService;
    private final CompetitionRegistrationItemService registrationItemService;
    private final CurrentUserService currentUserService;

    @GetMapping("/registrations/current-school")
    public ApiResponse<List<Map<String, Object>>> currentSchoolRegistrations() {
        Teacher teacher = currentUserService.getCurrentTeacher();
        return ApiResponse.ok(registrationService.listBySchool(teacher).stream()
                .map(registrationService::toMap)
                .toList());
    }

    @PostMapping("/{competitionId}/registrations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createDraft(
            @PathVariable Long competitionId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            String remark = body != null ? String.valueOf(body.getOrDefault("remark", "")) : "";
            CompetitionRegistration registration = registrationService.createDraft(teacher, competitionId, remark);
            return ResponseEntity.ok(ApiResponse.ok(registrationService.toMap(registration)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/registrations/{registrationId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable Long registrationId) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            CompetitionRegistration registration = registrationService.requireVisible(teacher, registrationId);
            Map<String, Object> result = new LinkedHashMap<>(registrationService.toMap(registration));
            result.put("items", registrationItemService.listByRegistration(registrationId).stream()
                    .map(registrationItemService::toMap)
                    .toList());
            result.put("approvalRecords", registrationService.listApprovalRecords(registrationId).stream()
                    .map(registrationService::toApprovalMap)
                    .toList());
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/registrations/{registrationId}/items")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> items(@PathVariable Long registrationId) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            registrationService.requireVisible(teacher, registrationId);
            return ResponseEntity.ok(ApiResponse.ok(registrationItemService.listByRegistration(registrationId).stream()
                    .map(registrationItemService::toMap)
                    .toList()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/registrations/{registrationId}/items")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addItem(
            @PathVariable Long registrationId,
            @RequestBody Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            CompetitionRegistration registration = registrationService.requireVisible(teacher, registrationId);
            return ResponseEntity.ok(ApiResponse.ok(registrationItemService.toMap(
                    registrationItemService.addItem(teacher, registration, body))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @DeleteMapping("/registrations/{registrationId}/items/{itemId}")
    public ResponseEntity<ApiResponse<String>> removeItem(
            @PathVariable Long registrationId,
            @PathVariable Long itemId) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            CompetitionRegistration registration = registrationService.requireVisible(teacher, registrationId);
            registrationItemService.removeItem(teacher, registration, itemId);
            return ResponseEntity.ok(ApiResponse.ok("????", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PutMapping("/registrations/{registrationId}/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(@PathVariable Long registrationId) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            CompetitionRegistration registration = registrationService.submit(teacher, registrationId);
            return ResponseEntity.ok(ApiResponse.ok(registrationService.toMap(registration)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PutMapping("/registrations/{registrationId}/district-review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> districtReview(
            @PathVariable Long registrationId,
            @RequestBody Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            CompetitionRegistration registration = registrationService.districtReview(
                    teacher,
                    registrationId,
                    String.valueOf(body.get("decision")),
                    body.get("comment") != null ? String.valueOf(body.get("comment")) : null);
            return ResponseEntity.ok(ApiResponse.ok(registrationService.toMap(registration)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PutMapping("/registrations/{registrationId}/city-review")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cityReview(
            @PathVariable Long registrationId,
            @RequestBody Map<String, Object> body) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            CompetitionRegistration registration = registrationService.cityReview(
                    teacher,
                    registrationId,
                    String.valueOf(body.get("decision")),
                    body.get("comment") != null ? String.valueOf(body.get("comment")) : null);
            return ResponseEntity.ok(ApiResponse.ok(registrationService.toMap(registration)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/registrations/{registrationId}/approval-records")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> approvalRecords(@PathVariable Long registrationId) {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            registrationService.requireVisible(teacher, registrationId);
            return ResponseEntity.ok(ApiResponse.ok(registrationService.listApprovalRecords(registrationId).stream()
                    .map(registrationService::toApprovalMap)
                    .toList()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
