package com.pe.assistant.repository;

import com.pe.assistant.entity.TeachingResource;
import com.pe.assistant.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeachingResourceRepository extends JpaRepository<TeachingResource, Long> {
    
    List<TeachingResource> findByUploader(Teacher uploader);
    
    List<TeachingResource> findByIsPublicTrue();
    
    List<TeachingResource> findByCategory(String category);
    
    List<TeachingResource> findBySubject(String subject);
    
    List<TeachingResource> findByGradeLevel(String gradeLevel);
    
    @Query("SELECT r FROM TeachingResource r WHERE r.isPublic = true OR r.uploader = :teacher ORDER BY r.createdAt DESC")
    List<TeachingResource> findAvailableResources(@Param("teacher") Teacher teacher);
    
    @Query("SELECT r FROM TeachingResource r WHERE (r.isPublic = true OR r.uploader = :teacher) AND r.category = :category ORDER BY r.createdAt DESC")
    List<TeachingResource> findAvailableResourcesByCategory(@Param("teacher") Teacher teacher, @Param("category") String category);
}