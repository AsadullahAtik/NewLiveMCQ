package com.livemcq.repo;

import com.livemcq.domain.OverallRank;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Read-only, like AttemptRankRepository: the class leaderboard comes from the overall_ranks view. */
public interface OverallRankRepository extends Repository<OverallRank, Long> {

    List<OverallRank> findByClassIdOrderByRankNoAscTotalTimeSecAsc(Long classId);

    Optional<OverallRank> findByUserId(Long userId);
}
