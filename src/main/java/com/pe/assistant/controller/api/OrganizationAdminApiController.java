package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.entity.Organization;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/org")
@RequiredArgsConstructor
public class OrganizationAdminApiController {

    private final OrganizationService organizationService;
    private final CurrentUserService currentUserService;

    @GetMapping("/tree")
    public ApiResponse<List<Map<String, Object>>> tree(
            @RequestParam(required = false) Long rootId) {
        Organization root = rootId != null
                ? organizationService.findById(rootId).orElse(null)
                : currentUserService.getCurrentManagedOrg();
        List<Organization> roots = organizationService.findVisibleRoots(root);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Organization org : roots) {
            result.add(toNode(org));
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/{orgId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long orgId) {
        Organization org = organizationService.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("组织节点不存在"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", org.getId());
        data.put("name", org.getName());
        data.put("code", org.getCode());
        data.put("type", org.getType());
        data.put("enabled", org.getEnabled());
        data.put("sortOrder", org.getSortOrder());
        data.put("contactPhone", org.getContactPhone());
        data.put("address", org.getAddress());
        data.put("fullName", org.getFullName());
        data.put("pathCodes", org.getPathCodes());
        data.put("parentId", org.getParent() != null ? org.getParent().getId() : null);
        data.put("parentName", org.getParent() != null ? org.getParent().getName() : null);
        data.put("childrenCount", organizationService.findChildren(org.getId()).size());
        return ApiResponse.ok(data);
    }

    private Map<String, Object> toNode(Organization org) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", org.getId());
        node.put("name", org.getName());
        node.put("code", org.getCode());
        node.put("type", org.getType());
        node.put("enabled", org.getEnabled());
        node.put("children", buildChildren(org));
        return node;
    }

    private List<Map<String, Object>> buildChildren(Organization parent) {
        List<Map<String, Object>> children = new ArrayList<>();
        for (Organization child : organizationService.findChildren(parent.getId())) {
            children.add(toNode(child));
        }
        return children;
    }
}