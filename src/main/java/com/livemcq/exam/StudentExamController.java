package com.livemcq.exam;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.livemcq.exam.StudentExamDtos.PaperView;
import com.livemcq.exam.StudentExamDtos.StudentExamView;
import com.livemcq.exam.StudentExamDtos.SubmitRequest;
import com.livemcq.exam.StudentExamDtos.SubmitResult;
import com.livemcq.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only STUDENTs reach /api/student/** (see SecurityConfig). */
@RestController
@RequestMapping("/api/student/exams")
@Tag(name = "Exams (student)")
public class StudentExamController {

    private final StudentExamService service;

    public StudentExamController(StudentExamService service) {
        this.service = service;
    }

    @GetMapping
    public List<StudentExamView> list(@AuthenticationPrincipal AuthPrincipal me) {
        return service.list(me);
    }

    @PostMapping("/{examId}/start")
    public PaperView start(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId) {
        return service.start(me, examId);
    }

    @PostMapping("/{examId}/submit")
    public SubmitResult submit(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId,
                               @Valid @RequestBody SubmitRequest body) {
        return service.submit(me, examId, body.answers());
    }
}
