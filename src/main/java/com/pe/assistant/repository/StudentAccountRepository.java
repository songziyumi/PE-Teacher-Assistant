package com.pe.assistant.repository;

import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentAccountRepository extends JpaRepository<StudentAccount, Long> {

    Optional<StudentAccount> findByLoginIdIgnoreCase(String loginId);

    boolean existsByLoginIdIgnoreCase(String loginId);

    Optional<StudentAccount> findByLoginAliasIgnoreCase(String loginAlias);

    boolean existsByLoginAliasIgnoreCase(String loginAlias);

    Optional<StudentAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<StudentAccount> findByStudent(Student student);

    Optional<StudentAccount> findByStudentId(Long studentId);

    List<StudentAccount> findByStudentIn(Collection<Student> students);
}
