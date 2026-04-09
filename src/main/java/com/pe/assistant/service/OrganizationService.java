package com.pe.assistant.service;

import com.pe.assistant.entity.Organization;
import com.pe.assistant.entity.OrganizationType;
import com.pe.assistant.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public Optional<Organization> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return organizationRepository.findById(id);
    }

    public Optional<Organization> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return organizationRepository.findByCode(code.trim());
    }

    public List<Organization> findChildren(Long parentId) {
        if (parentId == null) {
            return Collections.emptyList();
        }
        return organizationRepository.findByParentIdOrderBySortOrderAscNameAsc(parentId);
    }

    public List<Organization> findByType(OrganizationType type) {
        if (type == null) {
            return Collections.emptyList();
        }
        return organizationRepository.findByTypeOrderBySortOrderAscNameAsc(type);
    }

    @Transactional
    public Organization ensureNode(String code, String name, OrganizationType type, Organization parent, int sortOrder) {
        return organizationRepository.findByCode(code).orElseGet(() -> {
            Organization org = new Organization();
            org.setCode(code);
            org.setName(name);
            org.setType(type);
            org.setParent(parent);
            org.setSortOrder(sortOrder);
            org.setEnabled(true);
            org.setFullName(buildFullName(parent, name));
            org.setPathCodes(buildPathCodes(parent, code));
            org.setPathIds(parent != null && parent.getId() != null ? parent.getPathIds() : null);
            return organizationRepository.save(org);
        });
    }

    public List<Organization> findVisibleRoots(Organization managedOrg) {
        if (managedOrg == null) {
            return organizationRepository.findByParentIdOrderBySortOrderAscNameAsc(null);
        }
        return List.of(managedOrg);
    }

    private String buildFullName(Organization parent, String name) {
        if (parent == null || parent.getFullName() == null || parent.getFullName().isBlank()) {
            return name;
        }
        return parent.getFullName() + "/" + name;
    }

    private String buildPathCodes(Organization parent, String code) {
        if (parent == null || parent.getPathCodes() == null || parent.getPathCodes().isBlank()) {
            return code;
        }
        return parent.getPathCodes() + "/" + code;
    }

    public List<Organization> findSubtree(Organization root) {
        if (root == null || root.getId() == null) {
            return Collections.emptyList();
        }
        List<Organization> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private void collect(Organization current, List<Organization> result) {
        result.add(current);
        for (Organization child : findChildren(current.getId())) {
            collect(child, result);
        }
    }
}