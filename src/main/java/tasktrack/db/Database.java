package tasktrack.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * SQLite データベースへの接続を1本だけ保持し、JDBC の定型処理をまとめる。
 * アプリからの利用は単一スレッド（EDT）を前提とするが、念のため全操作を同期化している。
 */
public final class Database implements AutoCloseable {

    /** ResultSet の1行をオブジェクトへ変換する。 */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static final String IS_LOCAL_KEY =
            "substr(key, 1, " + AppStateRepository.LOCAL_PREFIX.length() + ") = '" + AppStateRepository.LOCAL_PREFIX + "'";

    private final Path file;
    private Connection conn;
    private int txDepth;

    private Database(Path file) {
        this.file = file;
    }

    /** DB ファイルを開き（無ければ作成し）、スキーマを最新化する。 */
    public static Database open(Path file) {
        Database db = new Database(file.toAbsolutePath().normalize());
        try {
            Path dir = db.file.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new DataAccessException("データ保存先を作成できません: " + file, e);
        }
        db.connect();
        try {
            Schema.migrate(db);
        } catch (RuntimeException e) {
            db.close();
            throw e;
        }
        return db;
    }

    public Path file() {
        return file;
    }

    private void connect() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + file);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                // 単一ファイルで完結させ（バックアップ・復元を単純にする）、電源断に備えて毎回 fsync する
                st.execute("PRAGMA journal_mode = DELETE");
                st.execute("PRAGMA synchronous = FULL");
            }
        } catch (SQLException e) {
            throw new DataAccessException("データベースを開けません: " + file, e);
        }
    }

    public synchronized <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("データの読み込みに失敗しました", e);
        }
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        return query(sql, mapper, params).stream().findFirst();
    }

    public synchronized int update(String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("データの保存に失敗しました", e);
        }
    }

    /** {@code body} を1トランザクションで実行する。例外時はロールバックする。入れ子呼び出しは外側に合流する。 */
    public synchronized <T> T transaction(Supplier<T> body) {
        if (txDepth > 0) {
            return body.get();
        }
        try {
            conn.setAutoCommit(false);
            txDepth++;
            try {
                T result = body.get();
                conn.commit();
                return result;
            } catch (RuntimeException | Error e) {
                conn.rollback();
                throw e;
            } finally {
                txDepth--;
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("トランザクションの実行に失敗しました", e);
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p instanceof Boolean b) {
                ps.setInt(i + 1, b ? 1 : 0);
            } else {
                ps.setObject(i + 1, p);
            }
        }
    }

    /**
     * 整合性の取れたスナップショットを {@code target} に書き出す（VACUUM INTO）。
     * 既存ファイルは置き換える。
     */
    public synchronized void backupTo(Path target) {
        Path abs = target.toAbsolutePath().normalize();
        if (isDatabaseFile(abs)) {
            throw new DataAccessException("使用中のデータベース自身をバックアップ先にはできません");
        }
        Path tmp = abs.resolveSibling(abs.getFileName() + ".tmp");
        try {
            Files.deleteIfExists(tmp);
            try (PreparedStatement ps = conn.prepareStatement("VACUUM INTO ?")) {
                ps.setString(1, tmp.toString());
                ps.execute();
            }
            removeLocalState(tmp);
            Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
        } catch (SQLException | IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // 後始末の失敗は元の例外を優先する
            }
            throw new DataAccessException("バックアップの作成に失敗しました: " + target, e);
        }
    }

    /** 復元直前の DB を退避しておくファイル。 */
    public Path preRestoreFile() {
        return file.resolveSibling(file.getFileName() + ".pre-restore");
    }

    /**
     * バックアップファイルで現在の DB を置き換える。
     * 事前にバックアップの妥当性を検査し、現在の DB は {@link #preRestoreFile()} に退避する。
     * 置き換え後に開けなかった場合は退避した DB へ戻す。
     */
    public synchronized void restoreFrom(Path backup) {
        Path src = backup.toAbsolutePath().normalize();
        if (isDatabaseFile(src)) {
            throw new DataAccessException("使用中のデータベース自身からは復元できません");
        }
        verifyBackup(src);
        // この PC 固有の設定は復元後も現在の値を維持する
        List<String[]> localState = query("SELECT key, value FROM app_state WHERE " + IS_LOCAL_KEY,
                rs -> new String[] {rs.getString(1), rs.getString(2)});
        Path saved = preRestoreFile();
        backupTo(saved);
        closeConnection();
        try {
            replaceDbFile(src);
            connect();
            Schema.migrate(this);
            replaceLocalState(localState);
        } catch (RuntimeException e) {
            closeConnection();
            try {
                replaceDbFile(saved);
            } finally {
                connect();
            }
            replaceLocalState(localState);
            throw new DataAccessException("復元に失敗したため、元のデータに戻しました", e);
        }
    }

    /** DB 内の PC 固有設定を {@code localState} の内容に入れ替える（バックアップ側に入っていた値は捨てる）。 */
    private void replaceLocalState(List<String[]> localState) {
        transaction(() -> {
            update("DELETE FROM app_state WHERE " + IS_LOCAL_KEY);
            for (String[] kv : localState) {
                update("INSERT INTO app_state (key, value) VALUES (?, ?)", kv[0], kv[1]);
            }
            return null;
        });
    }

    /** 書き出したバックアップから PC 固有設定を取り除く。VACUUM で空きページに残る値も消す。 */
    private static void removeLocalState(Path backupFile) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + backupFile);
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM app_state WHERE " + IS_LOCAL_KEY);
            st.execute("VACUUM");
        }
    }

    /**
     * {@code path} が使用中の DB ファイルそのものを指すか。
     * 文字列ではなく実体で比較する（{@code ..} を含むパス、シンボリックリンク、ハードリンク経由も検出する）。
     */
    private boolean isDatabaseFile(Path path) {
        try {
            if (Files.exists(path) && Files.isSameFile(path, file)) {
                return true;
            }
        } catch (IOException e) {
            // 判定できない場合は下の正規化パスの比較に任せる
        }
        return canonical(path).equals(canonical(file));
    }

    /** シンボリックリンクなどを解決したパス。ファイルが未作成の場合は親フォルダまでを解決する。 */
    private static Path canonical(Path path) {
        Path abs = path.toAbsolutePath().normalize();
        try {
            return abs.toRealPath();
        } catch (IOException e) {
            Path parent = abs.getParent();
            if (parent == null) {
                return abs;
            }
            try {
                return parent.toRealPath().resolve(abs.getFileName());
            } catch (IOException e2) {
                return abs;
            }
        }
    }

    private void replaceDbFile(Path src) {
        Path tmp = file.resolveSibling(file.getFileName() + ".restore-tmp");
        try {
            Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + "-journal"));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DataAccessException("データベースファイルを置き換えられません", e);
        }
    }

    /** 本アプリのバックアップとして妥当か（SQLite として健全で、対応可能なスキーマか）を検査する。 */
    private static void verifyBackup(Path src) {
        if (!Files.isRegularFile(src)) {
            throw new DataAccessException("バックアップファイルが見つかりません: " + src);
        }
        Database probe = new Database(src);
        try {
            probe.conn = DriverManager.getConnection("jdbc:sqlite:file:" + src.toUri().getRawPath() + "?mode=ro");
            String check = probe.queryOne("PRAGMA integrity_check", rs -> rs.getString(1)).orElse("");
            if (!"ok".equalsIgnoreCase(check)) {
                throw new DataAccessException("バックアップファイルが破損しています");
            }
            int version = Schema.currentVersion(probe);
            if (version < 1) {
                throw new DataAccessException("本アプリのバックアップファイルではありません");
            }
            if (version > Schema.CURRENT_VERSION) {
                throw new DataAccessException("新しいバージョンのアプリで作成されたバックアップのため復元できません");
            }
        } catch (SQLException e) {
            throw new DataAccessException("バックアップファイルを開けません（SQLite データベースではない可能性があります）", e);
        } catch (DataAccessException e) {
            if (e.getCause() instanceof SQLException) {
                throw new DataAccessException("バックアップファイルを読み取れません（SQLite データベースではない可能性があります）", e);
            }
            throw e;
        } finally {
            probe.closeConnection();
        }
    }

    private void closeConnection() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // クローズ失敗時にできることはない
            }
            conn = null;
        }
    }

    @Override
    public synchronized void close() {
        closeConnection();
    }
}
