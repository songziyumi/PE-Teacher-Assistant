package com.pe.assistant.service;

import com.pe.assistant.entity.Grade;
import com.pe.assistant.entity.School;
import com.pe.assistant.repository.GradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeRepository gradeRepository;

    public List<Grade> findAll(School school) {
        return gradeRepository.findBySchool(school);
    }

    public Grade findById(Long id) {
        return gradeRepository.findById(id).orElseThrow();
    }

    public Grade findByName(String name, School school) {
        return gradeRepository.findByNameAndSchool(name, school).orElse(null);
    }

    @Transactional
    public Grade create(String name, School school) {
        if (gradeRepository.existsByNameAndSchool(name, school)) throw new IllegalArgumentException("年级已存在");
        Grade g = new Grade();
        g.setName(name);
        g.setSchool(school);
        return gradeRepository.save(g);
    }

    @Transactional
    public Grade update(Long id, String name) {
        Grade g = gradeRepository.findById(id).orElseThrow();
        g.setName(name);
        return gradeRepository.save(g);
    }

    @Transactional
    public void delete(Long id) {
        gradeRepository.deleteById(id);
    }
}
