package com.livemcq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "sections")
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id", nullable = false, updatable = false)
    private Long institutionId;

    // LAZY: the class row is only read from the database when someone actually calls getSchoolClass()
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false, updatable = false)
    private SchoolClass schoolClass;

    @Column(nullable = false, length = 40)
    private String name;

    protected Section() { }

    public Section(SchoolClass schoolClass, String name) {
        this.schoolClass = schoolClass;
        this.institutionId = schoolClass.getInstitutionId();
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public SchoolClass getSchoolClass() { return schoolClass; }
    public String getName() { return name; }
}
