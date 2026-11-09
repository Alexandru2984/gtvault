package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.WordleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/wordle")
@Tag(name = "Tech Wordle", description = "Cuvântul tehnic al zilei")
public class WordleController {
    private final WordleService wordle;
    private final CurrentUser currentUser;

    public WordleController(WordleService wordle, CurrentUser currentUser) {
        this.wordle = wordle;
        this.currentUser = currentUser;
    }

    public record GuessRequest(String word) {}

    @GetMapping
    @Operation(summary = "Starea rundei de azi (vizitatorii văd doar regulile și statisticile comunității)")
    public Map<String, Object> state() {
        return wordle.state(currentUser.optionalId());
    }

    @PostMapping("/guess")
    @RateLimit(name = "wordle-guess", limit = 30, windowSeconds = 60)
    @Operation(summary = "Trimite o încercare de 5 litere")
    public Map<String, Object> guess(@RequestBody GuessRequest body) {
        return wordle.guess(currentUser.requireId(), body.word());
    }
}
