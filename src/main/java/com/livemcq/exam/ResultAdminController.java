package com.livemcq.exam;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.livemcq.exam.ResultDtos.*;
import com.livemcq.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Results (staff)")
public class ResultAdminController {

    private final ResultService results;

    public ResultAdminController(ResultService results) {
        this.results = results;
    }

    @GetMapping("/summary")
    public AdminSummary summary(@AuthenticationPrincipal AuthPrincipal me) {
        return results.summary(me.institutionId());
    }

    @PostMapping("/exams/{examId}/publish")
    public PublishResult publish(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId) {
        return results.publish(me.institutionId(), examId);
    }

    @DeleteMapping("/exams/{examId}/publish")
    public PublishResult unpublish(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId) {
        return results.unpublish(me.institutionId(), examId);
    }

    @PostMapping("/exams/{examId}/bonus")
    public BonusResult bonus(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId,
                             @Valid @RequestBody BonusRequest body) {
        return results.setBonus(me.institutionId(), examId, body.bonus());
    }

    @GetMapping("/exams/{examId}/leaderboard")
    public Leaderboard leaderboard(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId) {
        return results.adminLeaderboard(me.institutionId(), examId);
    }

    @GetMapping("/exams/{examId}/attempts/{studentId}")
    public AnswerSheet answerSheet(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId,
                                   @PathVariable long studentId) {
        return results.answerSheet(me.institutionId(), examId, studentId);
    }

    @GetMapping("/classes/{classId}/sections/{sectionId}/students")
    public SectionStudents sectionStudents(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long classId,
                                           @PathVariable long sectionId) {
        return results.sectionStudents(me.institutionId(), classId, sectionId);
    }

    @GetMapping("/students/{studentId}/report")
    public StudentReport report(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long studentId) {
        return results.report(me.institutionId(), studentId);
    }
}
