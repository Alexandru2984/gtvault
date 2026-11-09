package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.QuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quests")
@Tag(name = "Quests", description = "Misiuni zilnice și săptămânale")
public class QuestController {
    private final QuestService quests;
    private final CurrentUser currentUser;

    public QuestController(QuestService quests, CurrentUser currentUser) {
        this.quests = quests;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Misiunile de azi și din săptămâna curentă")
    public Map<String, Object> list() {
        return quests.list(currentUser.requireId());
    }

    @PostMapping("/{id}/claim")
    @RateLimit(name = "quest-claim", limit = 30, windowSeconds = 60)
    @Operation(summary = "Revendică recompensa unei misiuni finalizate")
    public Map<String, Object> claim(@PathVariable String id) {
        return quests.claim(currentUser.requireId(), id);
    }
}
