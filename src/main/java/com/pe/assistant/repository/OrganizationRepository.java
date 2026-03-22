package com.pe.assistant.repository;

import com.pe.assistant.entity.Organization;
import com.pe.assistant.entity.OrganizationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByCode(String code);
    boolean existsByCode(String code);
    List<Organization> findByParentIdOrderBySortOrderAscNameAsc(Long parentId);
    List<Organization> findByTypeOrderBySortOrderAscNameAsc(OrganizationType type);
}
