package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.QuizHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gamification/quiz/history")
public class QuizHistoryController {
    private final QuizHistoryService history;
    private final CurrentUser currentUser;

    public QuizHistoryController(QuizHistoryService history, CurrentUser currentUser) {
        this.history = history;
        this.currentUser = currentUser;
    }

    @GetMapping
    @RateLimit(name = "quiz-history", limit = 60, windowSeconds = 60)
    public QuizHistoryService.Page history(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(defaultValue = "ALL") String result,
                                           @RequestParam(defaultValue = "") String category) {
        return history.history(currentUser.requireId(), page, size, result, category);
    }

    @GetMapping("/{date}")
    @RateLimit(name = "quiz-history", limit = 60, windowSeconds = 60)
    public QuizHistoryService.Attempt detail(@PathVariable String date) {
        return history.detail(currentUser.requireId(), date);
    }
}
