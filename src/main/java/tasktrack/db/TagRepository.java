package tasktrack.db;

import java.util.List;
import java.util.Optional;

import tasktrack.domain.Tag;

/** タグマスタのデータアクセス。 */
public final class TagRepository {
    private static final Database.RowMapper<Tag> MAPPER = rs -> new Tag(rs.getString("id"), rs.getString("name"));

    private final Database db;

    public TagRepository(Database db) {
        this.db = db;
    }

    public List<Tag> findAll() {
        return db.query("SELECT * FROM tag ORDER BY name", MAPPER);
    }

    public Optional<Tag> findById(String id) {
        return db.queryOne("SELECT * FROM tag WHERE id = ?", MAPPER, id);
    }

    public Optional<Tag> findByName(String name) {
        return db.queryOne("SELECT * FROM tag WHERE name = ?", MAPPER, name);
    }

    public void insert(Tag tag) {
        db.update("INSERT INTO tag (id, name) VALUES (?, ?)", tag.id(), tag.name());
    }

    public void update(Tag tag) {
        db.update("UPDATE tag SET name = ? WHERE id = ?", tag.name(), tag.id());
    }

    /** タグを削除する。作業記録との紐付けは ON DELETE CASCADE で同時に削除される。 */
    public void delete(String id) {
        db.update("DELETE FROM tag WHERE id = ?", id);
    }
}
