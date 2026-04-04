package com.pe.assistant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class StudentReferenceCleanupService {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Boolean> tableExistsCache = new ConcurrentHashMap<>();

    public void deleteByStudentId(Long studentId) {
        deleteIfTableExists("competition_registration_item",
                "DELETE FROM competition_registration_item WHERE student_id = ?",
                studentId);
        deleteIfTableExists("competition_result",
                "DELETE FROM competition_result WHERE student_id = ?",
                studentId);
        deleteIfTableExists("student_accounts",
                "DELETE FROM student_accounts WHERE student_id = ?",
                studentId);
    }

    public void deleteAllBySchoolId(Long schoolId) {
        deleteIfTableExists("competition_registration_item",
                """
                DELETE cri
                FROM competition_registration_item cri
                JOIN students s ON s.id = cri.student_id
                WHERE s.school_id = ?
                """,
                schoolId);
        deleteIfTableExists("competition_result",
                """
                DELETE cr
                FROM competition_result cr
                JOIN students s ON s.id = cr.student_id
                WHERE s.school_id = ?
                """,
                schoolId);
        deleteIfTableExists("student_accounts",
                """
                DELETE sa
                FROM student_accounts sa
                JOIN students s ON s.id = sa.student_id
                WHERE s.school_id = ?
                """,
                schoolId);
    }

    private void deleteIfTableExists(String tableName, String sql, Long id) {
        if (!tableExists(tableName)) {
            return;
        }
        jdbcTemplate.update(sql, id);
    }

    private boolean tableExists(String tableName) {
        return tableExistsCache.computeIfAbsent(tableName, key -> {
            Integer count = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                    """,
                    Integer.class,
                    key
            );
            return count != null && count > 0;
        });
    }
}
