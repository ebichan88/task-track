package tasktrack.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import tasktrack.domain.TimeEntry;

/** 作業記録（およびタグ紐付け）のデータアクセス。 */
public final class TimeEntryRepository {
    private final Database db;

    public TimeEntryRepository(Database db) {
        this.db = db;
    }

    public void insert(TimeEntry e) {
        db.transaction(() -> {
            db.update("""
                    INSERT INTO time_entry (id, project_id, category_id, task_name, description,
                                            start_at, end_at, is_manual, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    e.id(), e.projectId(), e.categoryId(), e.taskName(), e.description(),
                    epoch(e.startAt()), epoch(e.endAt()), e.manual(), epoch(e.createdAt()), epoch(e.updatedAt()));
            insertTags(e);
            return null;
        });
    }

    public void update(TimeEntry e) {
        db.transaction(() -> {
            db.update("""
                    UPDATE time_entry
                       SET project_id = ?, category_id = ?, task_name = ?, description = ?,
                           start_at = ?, end_at = ?, is_manual = ?, updated_at = ?
                     WHERE id = ?""",
                    e.projectId(), e.categoryId(), e.taskName(), e.description(),
                    epoch(e.startAt()), epoch(e.endAt()), e.manual(), epoch(e.updatedAt()), e.id());
            db.update("DELETE FROM time_entry_tag WHERE entry_id = ?", e.id());
            insertTags(e);
            return null;
        });
    }

    private void insertTags(TimeEntry e) {
        for (String tagId : e.tagIds()) {
            db.update("INSERT INTO time_entry_tag (entry_id, tag_id) VALUES (?, ?)", e.id(), tagId);
        }
    }

    /** 終了日時を設定する。 */
    public void finish(String id, Instant endAt, Instant updatedAt) {
        db.update("UPDATE time_entry SET end_at = ?, updated_at = ? WHERE id = ?",
                epoch(endAt), epoch(updatedAt), id);
    }

    /** 作業記録を削除する。タグ紐付けは ON DELETE CASCADE で同時に削除される。 */
    public void delete(String id) {
        db.update("DELETE FROM time_entry WHERE id = ?", id);
    }

    public Optional<TimeEntry> findById(String id) {
        return db.queryOne("SELECT * FROM time_entry WHERE id = ?", this::map, id);
    }

    /** 終了日時が未設定の記録（開始日時順）。ユニークインデックスにより通常は高々1件。 */
    public List<TimeEntry> findUnfinished() {
        return db.query("SELECT * FROM time_entry WHERE end_at IS NULL ORDER BY start_at", this::map);
    }

    /**
     * 終了済みの記録のうち、区間 [from, to) と重なるものを開始日時順で返す。
     * 長さ0の記録は開始時刻が区間内にあれば含める。
     */
    public List<TimeEntry> findFinishedOverlapping(Instant from, Instant to) {
        return db.query("""
                SELECT * FROM time_entry
                 WHERE end_at IS NOT NULL AND start_at < ? AND (end_at > ? OR start_at >= ?)
                 ORDER BY start_at, created_at, id""",
                this::map, epoch(to), epoch(from), epoch(from));
    }

    private TimeEntry map(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        Set<String> tagIds = new LinkedHashSet<>(db.query(
                "SELECT tag_id FROM time_entry_tag WHERE entry_id = ? ORDER BY tag_id", r -> r.getString(1), id));
        long end = rs.getLong("end_at");
        Instant endAt = rs.wasNull() ? null : Instant.ofEpochSecond(end);
        return new TimeEntry(id, rs.getString("project_id"), rs.getString("category_id"),
                rs.getString("task_name"), rs.getString("description"),
                Instant.ofEpochSecond(rs.getLong("start_at")), endAt, rs.getInt("is_manual") != 0, tagIds,
                Instant.ofEpochSecond(rs.getLong("created_at")), Instant.ofEpochSecond(rs.getLong("updated_at")));
    }

    private static Long epoch(Instant t) {
        return t == null ? null : t.getEpochSecond();
    }
}
