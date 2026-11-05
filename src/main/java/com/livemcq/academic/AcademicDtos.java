package com.livemcq.academic;

import org.springframework.lang.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request and response shapes for classes, sections and students (grouped to keep the folder small). */
public final class AcademicDtos {

    private AcademicDtos() { }

    public record CreateClassRequest(@NotBlank @Size(max = 80) String name) { }

    public record CreateSectionRequest(@NotBlank @Size(max = 40) String name) { }

    public record MoveStudentRequest(@NotNull Long sectionId) { }

    public record SectionView(long id, String name, long studentCount) { }

    public record ClassView(long id, String name, long studentCount, long examCount, List<SectionView> sections) { }

    /** What the public sign-up form needs: classes and their sections, no counts. */
    public record ClassOption(long id, String name, List<SectionOption> sections) { }

    public record SectionOption(long id, String name) { }

    public record StudentView(long id, String name, String roll, @Nullable String phone,
                              @Nullable Long classId, @Nullable String className, @Nullable Long sectionId,
                              @Nullable String sectionName, long attempts) { }
}
