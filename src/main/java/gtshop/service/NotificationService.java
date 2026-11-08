package gtshop.service;

import gtshop.model.Notification;
import gtshop.repository.NotificationRepository;
import gtshop.util.Ids;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final TimeService time;

    public NotificationService(NotificationRepository repository, TimeService time) {
        this.repository = repository;
        this.time = time;
    }

    @Transactional
    public void notify(String userId, String type, String title, String body, String icon, String link) {
        repository.save(new Notification(Ids.prefixed("ntf-"), userId, type,
                truncate(title, 140), truncate(body, 500), truncate(icon, 16), truncate(link, 255), time.now()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String userId, int limit) {
        List<Notification> items = repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(Math.max(limit, 1), 50)));
        return Map.of("items", items, "unread", repository.countByUserIdAndReadFalse(userId));
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(String userId) {
        repository.markAllRead(userId);
    }

    @Transactional
    public void clear(String userId) {
        repository.deleteAllForUser(userId);
    }

    /** Notificarile mai vechi de 90 de zile sunt sterse automat. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOld() {
        repository.deleteOlderThan(time.now().minus(Duration.ofDays(90)));
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
