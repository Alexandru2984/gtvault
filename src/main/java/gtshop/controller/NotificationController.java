package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.service.NotificationService;
import gtshop.service.NotificationPreferencesService;
import gtshop.security.RateLimit;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Notificările mele")
public class NotificationController {
    private final NotificationService notifications;
    private final CurrentUser currentUser;
    private final NotificationPreferencesService preferences;

    public NotificationController(NotificationService notifications, CurrentUser currentUser, NotificationPreferencesService preferences) {
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.preferences = preferences;
    }

    @GetMapping("/preferences")
    @Operation(summary = "Preferințele mele pentru notificări în aplicație")
    public Map<String, Object> preferences() {
        return Map.of("preferences", preferences.get(currentUser.requireId()));
    }

    @PutMapping("/preferences")
    @RateLimit(name = "notification-preferences", limit = 30, windowSeconds = 3600)
    @Operation(summary = "Salvează categoriile de notificări opționale")
    public Map<String, Object> preferences(@Valid @RequestBody NotificationPreferencesService.Preferences request) {
        return Map.of("success", true, "preferences", preferences.save(currentUser.requireId(), request));
    }

    @GetMapping
    @Operation(summary = "Ultimele notificări și numărul celor necitite")
    public Map<String, Object> list(@RequestParam(defaultValue = "20") int limit) {
        return notifications.list(currentUser.requireId(), limit);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Numărul notificărilor necitite (pentru badge-ul din meniu)")
    public Map<String, Object> unread() {
        return Map.of("unread", notifications.unreadCount(currentUser.requireId()));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Marchează toate notificările ca citite")
    public Map<String, Object> readAll() {
        String id = currentUser.requireId();
        notifications.markAllRead(id);
        return Map.of("success", true, "unread", 0);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Marchează o notificare ca citită")
    public Map<String, Object> read(@PathVariable String id) {
        String userId = currentUser.requireId();
        notifications.markRead(userId, id);
        return Map.of("success", true, "unread", notifications.unreadCount(userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Șterge o notificare")
    public Map<String, Object> delete(@PathVariable String id) {
        String userId = currentUser.requireId();
        notifications.delete(userId, id);
        return Map.of("success", true, "unread", notifications.unreadCount(userId));
    }

    @DeleteMapping
    @Operation(summary = "Șterge toate notificările")
    public Map<String, Object> clear() {
        notifications.clear(currentUser.requireId());
        return Map.of("success", true, "unread", 0);
    }
}
