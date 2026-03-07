package com.pe.assistant.repository;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CourseSelectionRepository extends JpaRepository<CourseSelection, Long> {

    /** 某学生在某活动中的所有志愿/选课记录 */
    List<CourseSelection> findByEventAndStudent(SelectionEvent event, Student student);

    /** 某学生在某活动中已确认的课程（最多1条） */
    Optional<CourseSelection> findByEventAndStudentAndStatus(
            SelectionEvent event, Student student, String status);

    /** 某课程的所有记录，按提交时间排序（第二轮先到先得排序用） */
    List<CourseSelection> findByCourseAndStatusOrderBySelectedAtAsc(Course course, String status);

    /** 某课程某状态的记录数（统计用） */
    long countByCourseAndStatus(Course course, String status);

    /** 某活动中某状态的所有记录 */
    List<CourseSelection> findByEventAndStatus(SelectionEvent event, String status);

    /** 某学生某活动某志愿位 */
    Optional<CourseSelection> findByEventAndStudentAndPreference(
            SelectionEvent event, Student student, int preference);

    /** 某课程的报名名单（供管理员查看） */
    List<CourseSelection> findByCourseOrderBySelectedAtAsc(Course course);

    /** 某学生在某活动中是否已有确认记录 */
    boolean existsByEventAndStudentAndStatus(SelectionEvent event, Student student, String status);

    /** 第一轮某课程按班级分组报名人数（PER_CLASS抽签用） */
    @Query("SELECT cs FROM CourseSelection cs WHERE cs.course = :course AND cs.status = 'PENDING' AND cs.round = 1 AND cs.student.schoolClass.id = :classId ORDER BY cs.selectedAt ASC")
    List<CourseSelection> findPendingByClassId(
            @Param("course") Course course, @Param("classId") Long classId);
}
