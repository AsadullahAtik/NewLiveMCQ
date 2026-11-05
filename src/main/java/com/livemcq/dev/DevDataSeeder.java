package com.livemcq.dev;

import com.livemcq.domain.AppUser;
import com.livemcq.domain.Institution;
import com.livemcq.domain.Role;
import com.livemcq.domain.SchoolClass;
import com.livemcq.domain.Section;
import com.livemcq.repo.AppUserRepository;
import com.livemcq.repo.InstitutionRepository;
import com.livemcq.repo.SchoolClassRepository;
import com.livemcq.repo.SectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a demo institution with an admin, a teacher and a student so you can try the login by hand.
 * Only runs with the "dev" profile (mvn spring-boot:run -Dspring-boot.run.profiles=dev); it does
 * not exist in production. Safe to run again: it does nothing if the "demo" institution exists.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final InstitutionRepository institutions;
    private final SchoolClassRepository classes;
    private final SectionRepository sections;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public DevDataSeeder(InstitutionRepository institutions, SchoolClassRepository classes,
                         SectionRepository sections, AppUserRepository users, PasswordEncoder encoder) {
        this.institutions = institutions;
        this.classes = classes;
        this.sections = sections;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (institutions.findBySlug("demo").isPresent()) {
            return;
        }
        Institution inst = institutions.save(new Institution("Demo Coaching Centre", "demo"));
        SchoolClass class10 = classes.save(new SchoolClass(inst.getId(), "Class 10"));
        Section sectionA = sections.save(new Section(class10, "A"));
        users.save(new AppUser(inst.getId(), Role.ADMIN, "Admin", "admin", encoder.encode("admin"), null));
        users.save(new AppUser(inst.getId(), Role.TEACHER, "Demo Teacher", "teacher", encoder.encode("teacher"), null));
        users.save(new AppUser(inst.getId(), Role.STUDENT, "Demo Student", "1001", encoder.encode("1234"), sectionA));
        log.info("Dev data created. Login with institution=demo and roll/password: admin/admin, teacher/teacher, 1001/1234");
    }
}
