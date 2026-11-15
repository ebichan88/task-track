package tasktrack.db;

import java.util.List;
import java.util.Optional;

import tasktrack.domain.Category;
import tasktrack.domain.MasterItem;
import tasktrack.domain.Project;

/** プロジェクト／カテゴリ（同じ列構成のマスタ）のデータアクセス。 */
public final class MasterRepository<T extends MasterItem> {

    /** 行の値からマスタを生成する。 */
    @FunctionalInterface
    public interface Factory<T> {
        T create(String id, String name, String description, boolean active);
    }

    private final Database db;
    private final String table;
    private final Database.RowMapper<T> mapper;

    private MasterRepository(Database db, String table, Factory<T> factory) {
        this.db = db;
        this.table = table;
        this.mapper = rs -> factory.create(rs.getString("id"), rs.getString("name"),
                rs.getString("description"), rs.getInt("is_active") != 0);
    }

    public static MasterRepository<Project> projects(Database db) {
        return new MasterRepository<>(db, "project", Project::new);
    }

    public static MasterRepository<Category> categories(Database db) {
        return new MasterRepository<>(db, "category", Category::new);
    }

    /** 有効なものを先に、登録順で返す。 */
    public List<T> findAll() {
        return db.query("SELECT * FROM " + table + " ORDER BY is_active DESC, rowid", mapper);
    }

    public Optional<T> findById(String id) {
        return db.queryOne("SELECT * FROM " + table + " WHERE id = ?", mapper, id);
    }

    public Optional<T> findByName(String name) {
        return db.queryOne("SELECT * FROM " + table + " WHERE name = ?", mapper, name);
    }

    public void insert(T item) {
        db.update("INSERT INTO " + table + " (id, name, description, is_active) VALUES (?, ?, ?, ?)",
                item.id(), item.name(), item.description(), item.active());
    }

    public void update(T item) {
        db.update("UPDATE " + table + " SET name = ?, description = ?, is_active = ? WHERE id = ?",
                item.name(), item.description(), item.active(), item.id());
    }
}
