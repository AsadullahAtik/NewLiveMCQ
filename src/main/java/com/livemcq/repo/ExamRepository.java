package com.livemcq.repo;

import com.livemcq.domain.Exam;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByInstitutionIdOrderByCreatedAtDescIdDesc(Long institutionId);

    List<Exam> findBySchoolClassIdOrderByCreatedAtDescIdDesc(Long classId);

    Optional<Exam> findByIdAndInstitutionId(Long id, Long institutionId);

    long countBySchoolClassId(Long classId);
}
