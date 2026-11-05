package com.livemcq.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Admin, teacher or student. Named AppUser because "User" is awkward in SQL; the table is "users". */
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id", nullable = false, updatable = false)
    private Long institutionId;

    @Enumerated(EnumType.STRING)          // store "STUDENT", not the ordinal number 2
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String roll;

    // only ever a BCrypt hash (added with the login step) - never the real password
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    // students have a section (and through it a class); staff may have none
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() { }

    public AppUser(Long institutionId, Role role, String name, String roll, String passwordHash, Section section) {
        this.institutionId = institutionId;
        this.role = role;
        this.name = name;
        this.roll = roll;
        this.passwordHash = passwordHash;
        this.section = section;
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public Role getRole() { return role; }
    public String getName() { return name; }
    public String getRoll() { return roll; }
    public String getPasswordHash() { return passwordHash; }
    public String getPhone() { return phone; }
    public Section getSection() { return section; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setPhone(String phone) { this.phone = phone; }
    public void moveTo(Section newSection) { this.section = newSection; }
}
