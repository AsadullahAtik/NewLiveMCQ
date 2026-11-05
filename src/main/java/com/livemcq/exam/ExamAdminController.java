package com.livemcq.exam;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.livemcq.exam.ExamDtos.CreateExamRequest;
import com.livemcq.exam.ExamDtos.ExamDetail;
import com.livemcq.exam.ExamDtos.ExamSummary;
import com.livemcq.exam.ExamDtos.ReplaceQuestionsRequest;
import com.livemcq.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/exams")
@Tag(name = "Exams (staff)")
public class ExamAdminController {

    private final ExamAdminService service;

    public ExamAdminController(ExamAdminService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExamDetail create(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody CreateExamRequest body) {
        return service.create(me.institutionId(), body);
    }

    @GetMapping
    public List<ExamSummary> list(@AuthenticationPrincipal AuthPrincipal me) {
        return service.list(me.institutionId());
    }

    @GetMapping("/{examId}")
    public ExamDetail get(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId) {
        return service.get(me.institutionId(), examId);
    }

    @PutMapping("/{examId}/questions")
    public ExamDetail replaceQuestions(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId,
                                       @Valid @RequestBody ReplaceQuestionsRequest body) {
        return service.replaceQuestions(me.institutionId(), examId, body.questions());
    }

    @DeleteMapping("/{examId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId) {
        service.delete(me.institutionId(), examId);
    }
}
