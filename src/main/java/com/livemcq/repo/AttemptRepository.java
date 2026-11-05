package com.livemcq.repo;

import com.livemcq.domain.Attempt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    // "ExamId" = exam.id, "UserId" = user.id
    boolean existsByExamIdAndUserId(Long examId, Long userId);

    Optional<Attempt> findByExamIdAndUserId(Long examId, Long userId);

    List<Attempt> findByUserId(Long userId);

    long countByExamId(Long examId);

    long countByUserId(Long userId);
}
