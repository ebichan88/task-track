package tasktrack.db;

import java.util.Optional;

/** アプリケーション状態（キー・バリュー）のデータアクセス。 */
public final class AppStateRepository {
    /**
     * この PC 固有の設定（前回の出力先フォルダなど）を表すキーの接頭辞。
     * この接頭辞のキーはバックアップに含めず、復元時にもバックアップ側の値を取り込まない
     * （他の PC のパスや、細工されたネットワークパスを持ち込まないため）。
     */
    public static final String LOCAL_PREFIX = "local.";

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
