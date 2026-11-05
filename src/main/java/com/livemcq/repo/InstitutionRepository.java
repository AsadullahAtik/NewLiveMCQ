package com.livemcq.repo;

import com.livemcq.domain.Institution;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data builds the implementation at startup: just declare the interface.
 * The method name is the query: findBySlug -> "select i from Institution i where i.slug = ?".
 */
public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    Optional<Institution> findBySlug(String slug);
}
