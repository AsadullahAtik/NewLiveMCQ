package com.livemcq.exam;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.livemcq.exam.ResultDtos.Leaderboard;
import com.livemcq.exam.ResultDtos.MyResult;
import com.livemcq.exam.ResultDtos.OverallBoard;
import com.livemcq.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only STUDENTs; everything here is visible only after the exam's results are published. */
@RestController
@RequestMapping("/api/student")
@Tag(name = "Results (student)")
public class ResultStudentController {

    private final ResultService results;

    public ResultStudentController(ResultService results) {
        this.results = results;
    }

    @GetMapping("/exams/{examId}/result")
    public MyResult result(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId) {
        return results.myResult(me, examId);
    }

    @GetMapping("/exams/{examId}/leaderboard")
    public Leaderboard leaderboard(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long examId) {
        return results.myLeaderboard(me, examId);
    }

    @GetMapping("/leaderboard/overall")
    public OverallBoard overall(@AuthenticationPrincipal AuthPrincipal me) {
        return results.overallBoard(me);
    }
}
