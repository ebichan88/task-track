package tasktrack.db;

import java.util.Optional;

/** アプリケーション状態（キー・バリュー）のデータアクセス。 */
public final class AppStateRepository {
    private final Database db;

    public AppStateRepository(Database db) {
        this.db = db;
    }

    public Optional<String> get(String key) {
        return db.queryOne("SELECT value FROM app_state WHERE key = ?", rs -> rs.getString(1), key);
    }

    public void put(String key, String value) {
        db.update("INSERT INTO app_state (key, value) VALUES (?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value", key, value);
    }
}
