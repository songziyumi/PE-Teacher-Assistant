package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseClassCapacity;
import com.pe.assistant.entity.Grade;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.repository.CourseClassCapacityRepository;
import com.pe.assistant.repository.CourseSelectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ElectiveClassResolver {

    private final CourseClassCapacityRepository courseClassCapacityRepository;
    private final CourseSelectionRepository courseSelectionRepository;

    public ElectiveClassResolution resolve(Course course) {
        String baseName = normalizeName(course != null ? course.getName() : null);
        if (baseName == null) {
            return ElectiveClassResolution.empty();
        }

        GradeInference capacityInference = inferFromCapacityClasses(course);
        if (capacityInference.crossGrade()) {
            return new ElectiveClassResolution(baseName, null, true, true);
        }
        if (capacityInference.gradeResolved()) {
            return new ElectiveClassResolution(baseName, capacityInference.grade(), false, true);
        }

        GradeInference confirmedInference = inferFromSelections(
                courseSelectionRepository.findByCourseAndStatusOrderBySelectedAtAsc(course, "CONFIRMED"));
        if (confirmedInference.crossGrade()) {
            return new ElectiveClassResolution(baseName, null, true, true);
        }
        if (confirmedInference.gradeResolved()) {
            return new ElectiveClassResolution(baseName, confirmedInference.grade(), false, true);
        }

        GradeInference selectionInference = inferFromSelections(
                courseSelectionRepository.findByCourseOrderBySelectedAtAsc(course));
        if (selectionInference.crossGrade()) {
            return new ElectiveClassResolution(baseName, null, true, true);
        }
        if (selectionInference.gradeResolved()) {
            return new ElectiveClassResolution(baseName, selectionInference.grade(), false, true);
        }

        return new ElectiveClassResolution(baseName, null, false, false);
    }

    private GradeInference inferFromCapacityClasses(Course course) {
        Map<Long, Grade> gradesById = new LinkedHashMap<>();
        boolean hasUngradedCapacityClass = false;
        for (CourseClassCapacity capacity : courseClassCapacityRepository.findByCourse(course)) {
            SchoolClass schoolClass = capacity.getSchoolClass();
            if (schoolClass == null) {
                continue;
            }
            Grade grade = schoolClass.getGrade();
            if (grade == null || grade.getId() == null || normalizeName(grade.getName()) == null) {
                hasUngradedCapacityClass = true;
                continue;
            }
            gradesById.putIfAbsent(grade.getId(), grade);
        }

        if (!hasUngradedCapacityClass && gradesById.size() == 1) {
            return new GradeInference(gradesById.values().iterator().next(), false, true);
        }
        if (gradesById.size() > 1) {
            return new GradeInference(null, true, true);
        }
        return new GradeInference(null, false, false);
    }

    private GradeInference inferFromSelections(List<CourseSelection> selections) {
        Map<Long, Grade> gradesById = new LinkedHashMap<>();
        boolean hasUngradedSelection = false;
        for (CourseSelection selection : selections) {
            if (selection == null || selection.getStudent() == null) {
                continue;
            }
            SchoolClass schoolClass = selection.getStudent().getSchoolClass();
            Grade grade = schoolClass != null ? schoolClass.getGrade() : null;
            if (grade == null || grade.getId() == null || normalizeName(grade.getName()) == null) {
                hasUngradedSelection = true;
                continue;
            }
            gradesById.putIfAbsent(grade.getId(), grade);
        }

        if (!hasUngradedSelection && gradesById.size() == 1) {
            return new GradeInference(gradesById.values().iterator().next(), false, true);
        }
        if (gradesById.size() > 1) {
            return new GradeInference(null, true, true);
        }
        return new GradeInference(null, false, false);
    }

    public String buildStoredName(Course course) {
        return resolve(course).storedName();
    }

    public String buildStoredName(SchoolClass schoolClass) {
        String baseName = normalizeName(schoolClass != null ? schoolClass.getName() : null);
        if (baseName == null) {
            return null;
        }
        Grade grade = schoolClass.getGrade();
        if (grade == null || normalizeName(grade.getName()) == null) {
            return baseName;
        }
        return grade.getName().trim() + "/" + baseName;
    }

    private String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    public record ElectiveClassResolution(String baseName, Grade grade, boolean crossGrade, boolean gradeResolved) {
        public static ElectiveClassResolution empty() {
            return new ElectiveClassResolution(null, null, false, false);
        }

        public boolean hasSingleGrade() {
            return grade != null && !crossGrade;
        }

        public String storedName() {
            if (baseName == null) {
                return null;
            }
            if (!hasSingleGrade()) {
                return baseName;
            }
            return grade.getName().trim() + "/" + baseName;
        }
    }

    private record GradeInference(Grade grade, boolean crossGrade, boolean gradeResolved) {
    }
}
