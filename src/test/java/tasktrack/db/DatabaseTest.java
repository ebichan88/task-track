package tasktrack.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tasktrack.AppContext;
import tasktrack.app.EntryInput;
import tasktrack.app.ReviewInput;
import tasktrack.app.TaskSpec;
import tasktrack.app.ValidationException;
import tasktrack.domain.TimeEntry;

class DatabaseTest {
    @TempDir
    Path dir;

    private static TaskSpec spec(AppContext ctx, String name) {
        return new TaskSpec(null, ctx.masters.categories().get(0).id(), name, null, Set.of());
    }

    @Test
    void reopeningKeepsDataAndDoesNotReseedCategories() {
        Path file = dir.resolve("data").resolve("tasktrack.db");
        try (AppContext ctx = new AppContext(file, Clock.systemUTC())) {
            ctx.masters.createProject("案件A", "");
            ctx.appState.put("last_export_dir", "C:\\tmp");
            ctx.appState.put("last_export_dir", "C:\\out");
        }
        try (AppContext ctx = new AppContext(file, Clock.systemUTC())) {
            assertEquals(1, ctx.masters.projects().size());
            assertEquals(8, ctx.masters.categories().size());
            assertEquals("C:\\out", ctx.appState.get("last_export_dir").orElseThrow());
            assertEquals("1", ctx.appState.get("schema_version").orElseThrow());
        }
    }

    @Test
    void databaseAllowsOnlyOneUnfinishedEntry() {
        try (AppContext ctx = new AppContext(dir.resolve("t.db"), Clock.systemUTC())) {
            String cat = ctx.masters.categories().get(0).id();
            String insert = "INSERT INTO time_entry (id, category_id, task_name, start_at, end_at, is_manual, "
                    + "created_at, updated_at) VALUES (?, ?, 'x', 100, NULL, 0, 100, 100)";
            ctx.db.update(insert, "a", cat);
            assertThrows(DataAccessException.class, () -> ctx.db.update(insert, "b", cat));
        }
    }

    @Test
    void transactionRollsBackOnFailure() {
        try (Database db = Database.open(dir.resolve("t.db"))) {
            assertThrows(IllegalStateException.class, () -> db.transaction(() -> {
                db.update("INSERT INTO tag (id, name) VALUES ('1', 'x')");
                throw new IllegalStateException("boom");
            }));
            assertEquals(0, db.queryOne("SELECT count(*) FROM tag", rs -> rs.getInt(1)).orElseThrow());
        }
    }

    @Test
    void backupAndRestoreBringBackSavedData() {
        Path backup = dir.resolve("backup.db");
        try (AppContext ctx = new AppContext(dir.resolve("t.db"), Clock.systemUTC())) {
            TimeEntry kept = ctx.timer.start(spec(ctx, "バックアップ前の作業"));
            ctx.timer.stop();
            ctx.reviews.save(LocalDate.parse("2026-11-09"), new ReviewInput("成果", "", "", "", ""));

            ctx.backup.backup(backup);
            assertTrue(Files.isRegularFile(backup));
            // 上書きバックアップも可能
            ctx.backup.backup(backup);

            // バックアップ後の変更
            ctx.entries.delete(kept.id());
            ctx.masters.createProject("バックアップ後の案件", "");
            ctx.reviews.save(LocalDate.parse("2026-11-09"), new ReviewInput("", "", "", "", ""));

            ctx.backup.restore(backup);

            assertEquals("バックアップ前の作業", ctx.entries.find(kept.id()).orElseThrow().taskName());
            assertTrue(ctx.masters.projects().isEmpty());
            assertEquals("成果", ctx.reviews.find(LocalDate.parse("2026-11-09")).orElseThrow().achievements());
            // 復元直前の状態は退避されている
            assertTrue(Files.isRegularFile(ctx.backup.preRestoreFile()));
            // 復元後も通常どおり書き込める
            ctx.entries.createManual(new EntryInput(spec(ctx, "復元後"), java.time.Instant.ofEpochSecond(1000),
                    java.time.Instant.ofEpochSecond(2000)));
        }
    }

    @Test
    void backupAndRestoreRejectDatabaseItselfReachedThroughOtherPaths() throws IOException {
        Path db = dir.resolve("t.db");
        Files.createDirectories(dir.resolve("sub"));
        Path dotted = dir.resolve("sub").resolve("..").resolve("t.db");
        Path link = dir.resolve("link.db");
        Path hardLink = dir.resolve("hard.db");
        try (AppContext ctx = new AppContext(db, Clock.systemUTC())) {
            Files.createSymbolicLink(link, db);
            Files.createLink(hardLink, db);
            ctx.masters.createProject("現在のデータ", "");
            for (Path alias : new Path[] {dotted, link, hardLink}) {
                assertThrows(ValidationException.class, () -> ctx.backup.backup(alias), alias.toString());
                assertThrows(ValidationException.class, () -> ctx.backup.restore(alias), alias.toString());
            }
            // リンク先のフォルダ経由で、まだ存在しないファイル名として指定しても同じ
            Path dirLink = dir.resolve("dirlink");
            Files.createSymbolicLink(dirLink, dir);
            Files.delete(link);
            Files.delete(hardLink);
            assertThrows(ValidationException.class, () -> ctx.backup.backup(dirLink.resolve("t.db")));
            assertEquals(1, ctx.masters.projects().size());
            ctx.masters.createProject("まだ書き込める", "");
        }
        try (AppContext ctx = new AppContext(db, Clock.systemUTC())) {
            assertEquals(2, ctx.masters.projects().size());
        }
    }

    @Test
    void machineLocalSettingsAreNotBackedUpNorRestored() throws IOException {
        String localKey = AppStateRepository.LOCAL_PREFIX + "last_backup_dir";
        Path backup = dir.resolve("backup.db");
        Path malicious = dir.resolve("malicious.db");

        // 細工されたバックアップ: ネットワークパスをフォルダ設定に仕込んである
        try (AppContext other = new AppContext(dir.resolve("other.db"), Clock.systemUTC())) {
            other.masters.createProject("他所のデータ", "");
            other.db.backupTo(malicious);
        }
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + malicious);
             java.sql.Statement st = c.createStatement()) {
            st.execute("INSERT INTO app_state (key, value) VALUES ('" + localKey + "', '\\\\evil.invalid\\share')");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }

        try (AppContext ctx = new AppContext(dir.resolve("t.db"), Clock.systemUTC())) {
            ctx.appState.put(localKey, "C:\\Users\\me\\secret-client-folder");

            // バックアップには含まれない（削除済み領域にも残らない）
            ctx.backup.backup(backup);
            assertEquals("C:\\Users\\me\\secret-client-folder", ctx.appState.get(localKey).orElseThrow());
            String raw = new String(Files.readAllBytes(backup), java.nio.charset.StandardCharsets.ISO_8859_1);
            assertTrue(!raw.contains("secret-client-folder"));
            try (AppContext copy = new AppContext(backup, Clock.systemUTC())) {
                assertTrue(copy.appState.get(localKey).isEmpty());
                assertEquals("1", copy.appState.get("schema_version").orElseThrow());
            }

            // 復元してもバックアップ側の値は取り込まれず、この PC の値が残る
            ctx.backup.restore(malicious);
            assertEquals("他所のデータ", ctx.masters.projects().get(0).name());
            assertEquals("C:\\Users\\me\\secret-client-folder", ctx.appState.get(localKey).orElseThrow());
        }

        // この PC に設定が無い場合は、復元後も設定なしのまま
        try (AppContext fresh = new AppContext(dir.resolve("fresh.db"), Clock.systemUTC())) {
            fresh.backup.restore(malicious);
            assertTrue(fresh.appState.get(localKey).isEmpty());
        }
    }

    @Test
    void restoreRejectsInvalidFilesAndKeepsCurrentData() throws IOException {
        Path garbage = dir.resolve("garbage.db");
        Files.writeString(garbage, "this is not a sqlite database, just some text ".repeat(50));
        Path foreign = dir.resolve("foreign.db");
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + foreign);
             java.sql.Statement st = c.createStatement()) {
            st.execute("CREATE TABLE other (x INTEGER)");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }

        try (AppContext ctx = new AppContext(dir.resolve("t.db"), Clock.systemUTC())) {
            ctx.masters.createProject("現在のデータ", "");
            assertThrows(ValidationException.class, () -> ctx.backup.restore(garbage));
            assertThrows(ValidationException.class, () -> ctx.backup.restore(foreign));
            assertThrows(ValidationException.class, () -> ctx.backup.restore(dir.resolve("missing.db")));
            assertThrows(ValidationException.class, () -> ctx.backup.restore(dir.resolve("t.db")));
            assertThrows(ValidationException.class, () -> ctx.backup.backup(dir.resolve("t.db")));
            assertEquals(1, ctx.masters.projects().size());
            ctx.masters.createProject("まだ書き込める", "");
        }
    }
}
