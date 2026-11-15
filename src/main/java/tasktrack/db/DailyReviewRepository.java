package tasktrack.db;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import tasktrack.domain.DailyReview;

/** 日次振り返りのデータアクセス。 */
public final class DailyReviewRepository {
    private static final Database.RowMapper<DailyReview> MAPPER = rs -> new DailyReview(
            rs.getString("id"), LocalDate.parse(rs.getString("review_date")),
            rs.getString("achievements"), rs.getString("blockers"), rs.getString("decisions"),
            rs.getString("next_actions"), rs.getString("reflections"),
            Instant.ofEpochSecond(rs.getLong("created_at")), Instant.ofEpochSecond(rs.getLong("updated_at")));

    private final Database db;

    public DailyReviewRepository(Database db) {
        this.db = db;
    }

    public Optional<DailyReview> findByDate(LocalDate date) {
        return db.queryOne("SELECT * FROM daily_review WHERE review_date = ?", MAPPER, date.toString());
    }

    /** 対象日が from〜to（両端を含む）の振り返りを日付順で返す。 */
    public List<DailyReview> findBetween(LocalDate from, LocalDate to) {
        return db.query("SELECT * FROM daily_review WHERE review_date BETWEEN ? AND ? ORDER BY review_date",
                MAPPER, from.toString(), to.toString());
    }

    public void insert(DailyReview r) {
        db.update("""
                INSERT INTO daily_review (id, review_date, achievements, blockers, decisions,
                                          next_actions, reflections, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                r.id(), r.date().toString(), r.achievements(), r.blockers(), r.decisions(),
                r.nextActions(), r.reflections(), r.createdAt().getEpochSecond(), r.updatedAt().getEpochSecond());
    }

    public void update(DailyReview r) {
        db.update("""
                UPDATE daily_review
                   SET achievements = ?, blockers = ?, decisions = ?, next_actions = ?, reflections = ?,
                       updated_at = ?
                 WHERE id = ?""",
                r.achievements(), r.blockers(), r.decisions(), r.nextActions(), r.reflections(),
                r.updatedAt().getEpochSecond(), r.id());
    }

    public void delete(String id) {
        db.update("DELETE FROM daily_review WHERE id = ?", id);
    }
}
