package com.livemcq.repo;

import com.livemcq.domain.AttemptAnswer;
import com.livemcq.domain.AttemptAnswerId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, AttemptAnswerId> {

    // "IdAttemptId" = id.attemptId, a field inside the composite key
    List<AttemptAnswer> findByIdAttemptId(Long attemptId);
}
