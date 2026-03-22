package com.pe.assistant.service;

import com.pe.assistant.entity.Organization;
import com.pe.assistant.entity.OrganizationType;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrganizationScopeService {

    private final OrganizationService organizationService;
    private final SchoolRepository schoolRepository;

    public Organization resolveManagedOrg(Teacher teacher) {
        if (teacher == null) {
            return null;
        }
        if (teacher.getManagedOrg() != null) {
            return teacher.getManagedOrg();
        }
        School school = teacher.getSchool();
        return school == null ? null : school.getOrganization();
    }

    public boolean canManageSchool(Teacher teacher, School school) {
        if (teacher == null || school == null) {
            return false;
        }
        Organization managedOrg = resolveManagedOrg(teacher);
        Organization schoolOrg = school.getOrganization();
        if (managedOrg == null || schoolOrg == null) {
            return false;
        }
        return containsOrg(managedOrg, schoolOrg);
    }

    public Set<Long> listSchoolIdsInScope(Teacher teacher) {
        Organization managedOrg = resolveManagedOrg(teacher);
        if (managedOrg == null) {
            return Set.of();
        }
        Set<Long> schoolOrgIds = listOrgIdsInSubtree(managedOrg, OrganizationType.SCHOOL);
        Set<Long> schoolIds = new LinkedHashSet<>();
        for (Long schoolOrgId : schoolOrgIds) {
            schoolRepository.findByOrganizationId(schoolOrgId)
                    .map(School::getId)
                    .ifPresent(schoolIds::add);
        }
        return schoolIds;
    }

    public boolean containsOrg(Organization ancestor, Organization target) {
        if (ancestor == null || target == null || ancestor.getId() == null || target.getId() == null) {
            return false;
        }
        if (Objects.equals(ancestor.getId(), target.getId())) {
            return true;
        }
        Organization current = target.getParent();
        while (current != null) {
            if (Objects.equals(current.getId(), ancestor.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    public Set<Long> listOrgIdsInSubtree(Organization root, OrganizationType filterType) {
        if (root == null || root.getId() == null) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        Deque<Organization> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Organization current = queue.removeFirst();
            if (filterType == null || current.getType() == filterType) {
                result.add(current.getId());
            }
            queue.addAll(organizationService.findChildren(current.getId()));
        }
        return result;
    }

    public List<Organization> listPathToRoot(Organization org) {
        List<Organization> path = new ArrayList<>();
        Organization current = org;
        while (current != null) {
            path.add(0, current);
            current = current.getParent();
        }
        return path;
    }
}
