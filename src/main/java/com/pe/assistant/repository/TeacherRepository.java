package com.pe.assistant.repository;

import com.pe.assistant.entity.Teacher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Optional<Teacher> findByUsername(String username);
    boolean existsByUsername(String username);

    @Query("SELECT t FROM Teacher t WHERE " +
           "(:name IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:username IS NULL OR LOWER(t.username) LIKE LOWER(CONCAT('%', :username, '%'))) AND " +
           "(:phone IS NULL OR t.phone LIKE CONCAT('%', :phone, '%'))")
    Page<Teacher> findByFilters(@Param("name") String name,
                                @Param("username") String username,
                                @Param("phone") String phone,
                                Pageable pageable);
}
