package com.livemcq.academic;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.livemcq.academic.AcademicDtos.ClassOption;
import com.livemcq.academic.AcademicDtos.ClassView;
import com.livemcq.academic.AcademicDtos.CreateClassRequest;
import com.livemcq.academic.AcademicDtos.CreateSectionRequest;
import com.livemcq.academic.AcademicDtos.MoveStudentRequest;
import com.livemcq.academic.AcademicDtos.SectionView;
import com.livemcq.academic.AcademicDtos.StudentView;
import com.livemcq.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Classes, sections and students")
public class AcademicController {

    private final AcademicService academic;

    public AcademicController(AcademicService academic) {
        this.academic = academic;
    }

    /** Public: the sign-up form lists the classes of the institution whose slug it was given. */
    @SecurityRequirements
    @GetMapping("/api/public/institutions/{slug}/classes")
    public List<ClassOption> publicClasses(@PathVariable String slug) {
        return academic.publicClasses(slug);
    }

    @GetMapping("/api/admin/classes")
    public List<ClassView> classes(@AuthenticationPrincipal AuthPrincipal me) {
        return academic.classes(me.institutionId());
    }

    @PostMapping("/api/admin/classes")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassView createClass(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody CreateClassRequest body) {
        return academic.createClass(me.institutionId(), body.name());
    }

    @PostMapping("/api/admin/classes/{classId}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public SectionView addSection(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long classId,
                                  @Valid @RequestBody CreateSectionRequest body) {
        return academic.addSection(me.institutionId(), classId, body.name());
    }

    @GetMapping("/api/admin/students")
    public List<StudentView> students(@AuthenticationPrincipal AuthPrincipal me) {
        return academic.students(me.institutionId());
    }

    @PatchMapping("/api/admin/students/{studentId}/enrollment")
    public StudentView move(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long studentId,
                            @Valid @RequestBody MoveStudentRequest body) {
        return academic.moveStudent(me.institutionId(), studentId, body.sectionId());
    }
}
