package gtshop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gtshop.game.ContentBank;
import gtshop.web.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Caiet privat de învățare. Citirea nu acordă puncte și nu modifică misiuni. */
@Service
public class QuizHistoryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TimeService time;

    public QuizHistoryService(JdbcTemplate jdbc, ObjectMapper json, TimeService time) {
        this.jdbc = jdbc;
        this.json = json;
        this.time = time;
    }

    public record Attempt(String date, String questionId, String category, String question, List<String> options,
                          int selectedIndex, int correctIndex, String explanation, int rewardPoints, Instant answeredAt) {
        public boolean isCorrect() { return selectedIndex == correctIndex; }
    }
    public record Item(String date, String category, String question, boolean correct, int rewardPoints) {}
    public record Category(String name, long answered, long correct) {}
    public record Summary(long answered, long correct, long rewardPoints, List<Category> categories) {}
    public record Page(List<Item> items, int page, int size, long totalItems, long totalPages, Summary summary) {}

    /** Apelată în tranzacția quiz-ului, sub blocarea contului folosită și pentru creditare. */
    public void record(String userId, LocalDate day, ContentBank.DailyQuestion question, int selected, int reward) {
        try {
            jdbc.update("""
                    INSERT INTO quiz_attempts (user_id, game_date, question_id, category, question, options,
                        selected_index, correct_index, explanation, reward_points, answered_at)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)
                    """, userId, day, question.source().id(), question.source().category(),
                    question.source().question(), json.writeValueAsString(question.options()), selected,
                    question.correctIndex(), question.source().explanation(), reward, Timestamp.from(time.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Întrebarea nu poate fi salvată", e);
        }
    }

    public Optional<Attempt> find(String userId, LocalDate day) {
        return jdbc.query("SELECT * FROM quiz_attempts WHERE user_id = ? AND game_date = ?", this::attempt, userId, day)
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Page history(String userId, int page, int size, String result, String category) {
        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(20, size));
        String filter = result == null || result.isBlank() ? "ALL" : result.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "CORRECT", "WRONG").contains(filter)) {
            throw ApiException.badRequest("Filtru invalid. Alege ALL, CORRECT sau WRONG.");
        }
        String topic = category == null ? "" : category.trim();
        if (topic.length() > 100) throw ApiException.badRequest("Categoria este prea lungă.");
        String where = """
                WHERE user_id = ? AND game_date <= ? AND (? = '' OR category = ?)
                    AND (? = 'ALL' OR (selected_index = correct_index) = (? = 'CORRECT'))
                """;
        long total = jdbc.queryForObject("SELECT count(*) FROM quiz_attempts " + where, Long.class,
                userId, time.today(), topic, topic, filter, filter);
        long offset = (long) p * s;
        List<Item> items = offset >= total ? List.of() : jdbc.query("""
                SELECT game_date, category, question, selected_index = correct_index AS correct, reward_points
                FROM quiz_attempts
                """ + where + " ORDER BY game_date DESC LIMIT ? OFFSET ?", (rs, row) -> new Item(
                rs.getDate("game_date").toLocalDate().toString(), rs.getString("category"), rs.getString("question"),
                rs.getBoolean("correct"), rs.getInt("reward_points")), userId, time.today(), topic, topic, filter, filter, s, offset);
        return new Page(items, p, s, total, (total + s - 1) / s, summary(userId));
    }

    @Transactional(readOnly = true)
    public Attempt detail(String userId, String date) {
        LocalDate day;
        try {
            if (date == null || date.length() != 10) throw new IllegalArgumentException();
            day = LocalDate.parse(date);
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            throw ApiException.badRequest("Data trebuie să fie o zi validă în formatul AAAA-LL-ZZ.");
        }
        if (day.isAfter(time.today())) throw ApiException.notFound("Răspunsul nu a fost găsit.");
        return find(userId, day).orElseThrow(() -> ApiException.notFound("Răspunsul nu a fost găsit."));
    }

    private Summary summary(String userId) {
        List<Category> categories = jdbc.query("""
                SELECT category, count(*) AS answered, count(*) FILTER (WHERE selected_index = correct_index) AS correct
                FROM quiz_attempts WHERE user_id = ? AND game_date <= ? GROUP BY category ORDER BY category
                """, (rs, row) -> new Category(rs.getString("category"), rs.getLong("answered"), rs.getLong("correct")), userId, time.today());
        long rewards = jdbc.queryForObject("SELECT COALESCE(sum(reward_points), 0) FROM quiz_attempts WHERE user_id = ? AND game_date <= ?",
                Long.class, userId, time.today());
        return new Summary(categories.stream().mapToLong(Category::answered).sum(),
                categories.stream().mapToLong(Category::correct).sum(), rewards, categories);
    }

    private Attempt attempt(ResultSet rs, int row) throws SQLException {
        try {
            return new Attempt(rs.getDate("game_date").toLocalDate().toString(), rs.getString("question_id"),
                    rs.getString("category"), rs.getString("question"),
                    json.readValue(rs.getString("options"), new TypeReference<List<String>>() {}),
                    rs.getInt("selected_index"), rs.getInt("correct_index"), rs.getString("explanation"),
                    rs.getInt("reward_points"), rs.getTimestamp("answered_at").toInstant());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Întrebarea salvată nu poate fi citită", e);
        }
    }
}
