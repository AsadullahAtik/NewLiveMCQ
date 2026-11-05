package com.livemcq.repo;

import com.livemcq.domain.AppUser;
import com.livemcq.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // Login looks a user up by roll inside ONE institution, ignoring case (matches the unique index).
    @Query("select u from AppUser u where u.institutionId = :institutionId and lower(u.roll) = lower(:roll)")
    Optional<AppUser> findByRoll(@Param("institutionId") Long institutionId, @Param("roll") String roll);

    List<AppUser> findByInstitutionIdAndRole(Long institutionId, Role role);

    List<AppUser> findBySectionId(Long sectionId);

    Optional<AppUser> findByIdAndInstitutionId(Long id, Long institutionId);

    long countBySectionId(Long sectionId);

    long countByInstitutionIdAndRole(Long institutionId, Role role);
}
