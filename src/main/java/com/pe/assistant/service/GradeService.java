package com.pe.assistant.service;

import com.pe.assistant.entity.Grade;
import com.pe.assistant.repository.GradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeRepository gradeRepository;

    public List<Grade> findAll() {
        return gradeRepository.findAll();
    }

    public Grade findById(Long id) {
        return gradeRepository.findById(id).orElseThrow();
    }

    public Grade findByName(String name) {
        return gradeRepository.findByName(name).orElse(null);
    }

    @Transactional
    public Grade create(String name) {
        if (gradeRepository.existsByName(name)) throw new IllegalArgumentException("年级已存在");
        Grade g = new Grade();
        g.setName(name);
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
