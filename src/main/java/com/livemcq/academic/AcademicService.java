package com.livemcq.academic;

import com.livemcq.academic.AcademicDtos.ClassOption;
import com.livemcq.academic.AcademicDtos.ClassView;
import com.livemcq.academic.AcademicDtos.SectionOption;
import com.livemcq.academic.AcademicDtos.SectionView;
import com.livemcq.academic.AcademicDtos.StudentView;
import com.livemcq.domain.AppUser;
import com.livemcq.domain.Role;
import com.livemcq.domain.SchoolClass;
import com.livemcq.domain.Section;
import com.livemcq.repo.AppUserRepository;
import com.livemcq.repo.AttemptRepository;
import com.livemcq.repo.ExamRepository;
import com.livemcq.repo.InstitutionRepository;
import com.livemcq.repo.SchoolClassRepository;
import com.livemcq.repo.SectionRepository;
import com.livemcq.web.ApiException;
import com.livemcq.web.Text;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method takes the caller's institutionId and uses it in every lookup: a class id that belongs to
 * another institution is simply "not found", exactly as if it did not exist.
 */
@Service
@Transactional(readOnly = true)   // default for the class; methods that write say @Transactional themselves
public class AcademicService {

    private static final String DEFAULT_SECTION = "ক";   // same default section as the prototype

    private final SchoolClassRepository classes;
    private final SectionRepository sections;
    private final AppUserRepository users;
    private final ExamRepository exams;
    private final AttemptRepository attempts;
    private final InstitutionRepository institutions;

    public AcademicService(SchoolClassRepository classes, SectionRepository sections, AppUserRepository users,
                           ExamRepository exams, AttemptRepository attempts, InstitutionRepository institutions) {
        this.classes = classes;
        this.sections = sections;
        this.users = users;
        this.exams = exams;
        this.attempts = attempts;
        this.institutions = institutions;
    }

    // N+1 note: this runs one small query per class/section. Fine for tens of classes; a single
    // GROUP BY query is the fix if it ever gets slow (measure first).
    public List<ClassView> classes(long institutionId) {
        return classes.findByInstitutionIdOrderByName(institutionId).stream().map(this::toView).toList();
    }

    @Transactional
    public ClassView createClass(long institutionId, String rawName) {
        String name = Text.trim(rawName);
        if (name.length() < 2) {
            throw ApiException.unprocessable("CLASS_NAME_TOO_SHORT", "Class name needs at least 2 characters");
        }
        if (classes.existsByInstitutionIdAndName(institutionId, name)) {
            throw ApiException.conflict("CLASS_EXISTS", "A class with this name already exists");
        }
        try {
            SchoolClass c = classes.saveAndFlush(new SchoolClass(institutionId, name));
            sections.saveAndFlush(new Section(c, DEFAULT_SECTION));
            return toView(c);
        } catch (DataIntegrityViolationException e) {   // two admins adding the same name at the same moment
            throw ApiException.conflict("CLASS_EXISTS", "A class with this name already exists");
        }
    }

    @Transactional
    public SectionView addSection(long institutionId, long classId, String rawName) {
        SchoolClass c = classes.findByIdAndInstitutionId(classId, institutionId)
                .orElseThrow(() -> ApiException.notFound("CLASS_NOT_FOUND", "Class not found"));
        String name = Text.trim(rawName);
        if (name.isEmpty()) {
            throw ApiException.unprocessable("SECTION_NAME_REQUIRED", "Section name is required");
        }
        if (sections.existsBySchoolClassIdAndName(c.getId(), name)) {
            throw ApiException.conflict("SECTION_EXISTS", "This section already exists in the class");
        }
        try {
            Section s = sections.saveAndFlush(new Section(c, name));
            return new SectionView(s.getId(), s.getName(), 0);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("SECTION_EXISTS", "This section already exists in the class");
        }
    }

    /** For the sign-up form, before anyone is logged in. The institution is identified by its public slug. */
    public List<ClassOption> publicClasses(String slug) {
        long institutionId = institutions.findBySlug(slug.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("INSTITUTION_NOT_FOUND", "Institution not found"))
                .getId();
        return classes.findByInstitutionIdOrderByName(institutionId).stream()
                .map(c -> new ClassOption(c.getId(), c.getName(),
                        sections.findBySchoolClassIdOrderByName(c.getId()).stream()
                                .map(s -> new SectionOption(s.getId(), s.getName())).toList()))
                .toList();
    }

    public List<StudentView> students(long institutionId) {
        return users.findByInstitutionIdAndRole(institutionId, Role.STUDENT).stream()
                .map(this::toStudentView)
                .sorted(java.util.Comparator.comparingInt((StudentView v) -> v.roll().length()).thenComparing(StudentView::roll))
                .toList();
    }

    @Transactional
    public StudentView moveStudent(long institutionId, long studentId, long sectionId) {
        AppUser student = users.findByIdAndInstitutionId(studentId, institutionId)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> ApiException.notFound("STUDENT_NOT_FOUND", "Student not found"));
        Section target = sections.findByIdAndInstitutionId(sectionId, institutionId)
                .orElseThrow(() -> ApiException.unprocessable("SECTION_INVALID", "Choose a valid class and section"));
        student.moveTo(target);
        return toStudentView(student);
    }

    private ClassView toView(SchoolClass c) {
        List<SectionView> views = sections.findBySchoolClassIdOrderByName(c.getId()).stream()
                .map(s -> new SectionView(s.getId(), s.getName(), users.countBySectionId(s.getId())))
                .toList();
        long students = views.stream().mapToLong(SectionView::studentCount).sum();
        return new ClassView(c.getId(), c.getName(), students, exams.countBySchoolClassId(c.getId()), views);
    }

    private StudentView toStudentView(AppUser u) {
        Section s = u.getSection();
        return new StudentView(u.getId(), u.getName(), u.getRoll(), u.getPhone(),
                s == null ? null : s.getSchoolClass().getId(),
                s == null ? null : s.getSchoolClass().getName(),
                s == null ? null : s.getId(),
                s == null ? null : s.getName(),
                attempts.countByUserId(u.getId()));
    }
}
