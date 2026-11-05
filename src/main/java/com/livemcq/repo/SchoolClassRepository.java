package com.livemcq.repo;

import com.livemcq.domain.SchoolClass;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    List<SchoolClass> findByInstitutionIdOrderByName(Long institutionId);

    // every lookup by id ALSO filters by institution: that is what keeps tenants apart
    Optional<SchoolClass> findByIdAndInstitutionId(Long id, Long institutionId);

    boolean existsByInstitutionIdAndName(Long institutionId, String name);
}
