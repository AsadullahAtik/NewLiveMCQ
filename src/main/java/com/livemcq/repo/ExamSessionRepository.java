package com.livemcq.repo;

import com.livemcq.domain.ExamSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    Optional<ExamSession> findByExamIdAndUserId(Long examId, Long userId);

    long countByExamId(Long examId);
}
