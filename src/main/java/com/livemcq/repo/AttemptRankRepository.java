package com.livemcq.repo;

import com.livemcq.domain.AttemptRank;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Read-only: it extends the bare Repository marker (not JpaRepository), so there is no save() or
 * delete() to call by mistake. Results, ranks and leaderboards all come from the database view.
 */
public interface AttemptRankRepository extends Repository<AttemptRank, Long> {

    /** The leaderboard of one exam, best rank first; the same rank is listed by shorter time first. */
    List<AttemptRank> findByExamIdOrderByRankNoAscTimeSecAsc(Long examId);

    Optional<AttemptRank> findByExamIdAndUserId(Long examId, Long userId);

    List<AttemptRank> findByUserId(Long userId);
}
