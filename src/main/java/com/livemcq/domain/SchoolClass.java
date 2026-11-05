package com.livemcq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "school_classes")
public class SchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // the tenant is kept as a plain id: cheap to load, and we never navigate "class -> institution"
    @Column(name = "institution_id", nullable = false, updatable = false)
    private Long institutionId;

    @Column(nullable = false, length = 80)
    private String name;

    protected SchoolClass() { }

    public SchoolClass(Long institutionId, String name) {
        this.institutionId = institutionId;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public String getName() { return name; }
}
