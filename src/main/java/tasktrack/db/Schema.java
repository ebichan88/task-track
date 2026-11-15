package tasktrack.db;

import java.util.List;
import java.util.UUID;

/** スキーマ定義とマイグレーション。バージョンは app_state.schema_version で管理する。 */
final class Schema {
    static final int CURRENT_VERSION = 1;
    static final String VERSION_KEY = "schema_version";

    private static final List<String> V1 = List.of(
            """
            CREATE TABLE app_state (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )""",
            """
            CREATE TABLE project (
                id          TEXT PRIMARY KEY,
                name        TEXT NOT NULL UNIQUE,
                description TEXT NOT NULL DEFAULT '',
                is_active   INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1))
            )""",
            """
            CREATE TABLE category (
                id          TEXT PRIMARY KEY,
                name        TEXT NOT NULL UNIQUE,
                description TEXT NOT NULL DEFAULT '',
                is_active   INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1))
            )""",
            """
            CREATE TABLE tag (
                id   TEXT PRIMARY KEY,
                name TEXT NOT NULL UNIQUE
            )""",
            // 日時はすべて UTC のエポック秒（INTEGER）で保持する
            """
            CREATE TABLE time_entry (
                id          TEXT PRIMARY KEY,
                project_id  TEXT REFERENCES project(id),
                category_id TEXT NOT NULL REFERENCES category(id),
                task_name   TEXT NOT NULL CHECK (length(trim(task_name)) > 0),
                description TEXT,
                start_at    INTEGER NOT NULL,
                end_at      INTEGER,
                is_manual   INTEGER NOT NULL CHECK (is_manual IN (0, 1)),
                created_at  INTEGER NOT NULL,
                updated_at  INTEGER NOT NULL,
                CHECK (end_at IS NULL OR end_at >= start_at)
            )""",
            "CREATE INDEX ix_time_entry_start ON time_entry (start_at)",
            // 未終了（計測中）の記録は同時に1件まで、を DB レベルで保証する
            "CREATE UNIQUE INDEX ux_time_entry_running ON time_entry ((end_at IS NULL)) WHERE end_at IS NULL",
            """
            CREATE TABLE time_entry_tag (
                entry_id TEXT NOT NULL REFERENCES time_entry(id) ON DELETE CASCADE,
                tag_id   TEXT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
                PRIMARY KEY (entry_id, tag_id)
            )""",
            """
            CREATE TABLE daily_review (
                id           TEXT PRIMARY KEY,
                review_date  TEXT NOT NULL UNIQUE,
                achievements TEXT NOT NULL DEFAULT '',
                blockers     TEXT NOT NULL DEFAULT '',
                decisions    TEXT NOT NULL DEFAULT '',
                next_actions TEXT NOT NULL DEFAULT '',
                reflections  TEXT NOT NULL DEFAULT '',
                created_at   INTEGER NOT NULL,
                updated_at   INTEGER NOT NULL
            )""");

    private static final String[][] INITIAL_CATEGORIES = {
            {"設計・技術調査", "設計検討、技術調査、仕様確認など"},
            {"実装・コーディング", "プログラム実装、修正など"},
            {"レビュー", "コードレビュー、設計レビューなど"},
            {"テスト・品質保証", "テスト設計、テスト実施、品質確認など"},
            {"会議・コミュニケーション", "会議、相談、打ち合わせなど"},
            {"問い合わせ・障害対応", "問い合わせ対応、障害調査など"},
            {"プロジェクト管理", "進捗管理、タスク管理など"},
            {"その他", "上記に分類できない作業"},
    };

    private Schema() {
    }

    /** DB を最新スキーマへ更新する。新しすぎる DB（将来バージョン）は拒否する。 */
    static void migrate(Database db) {
        int version = currentVersion(db);
        if (version > CURRENT_VERSION) {
            throw new DataAccessException("このデータベースは新しいバージョンのアプリで作成されています (schema_version="
                    + version + ")");
        }
        if (version < 1) {
            db.transaction(() -> {
                V1.forEach(db::update);
                for (String[] c : INITIAL_CATEGORIES) {
                    db.update("INSERT INTO category (id, name, description, is_active) VALUES (?, ?, ?, 1)",
                            UUID.randomUUID().toString(), c[0], c[1]);
                }
                db.update("INSERT INTO app_state (key, value) VALUES (?, ?)", VERSION_KEY, "1");
                return null;
            });
        }
    }

    /** スキーマ未作成なら 0。 */
    static int currentVersion(Database db) {
        boolean hasState = db.queryOne(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'app_state'", rs -> 1).isPresent();
        if (!hasState) {
            return 0;
        }
        return db.queryOne("SELECT value FROM app_state WHERE key = ?", rs -> rs.getString(1), VERSION_KEY)
                .map(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        throw new DataAccessException("schema_version が不正です: " + v);
                    }
                })
                .orElse(0);
    }
}
