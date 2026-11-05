package com.livemcq.repo;

import com.livemcq.domain.Section;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {

    // "SchoolClassId" walks the relationship: section.schoolClass.id
    List<Section> findBySchoolClassIdOrderByName(Long classId);

    Optional<Section> findByIdAndInstitutionId(Long id, Long institutionId);

    boolean existsBySchoolClassIdAndName(Long classId, String name);
}
