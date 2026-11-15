package tasktrack.app;

import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;

import tasktrack.db.DataAccessException;
import tasktrack.db.Database;

/** データベースのバックアップ・復元。 */
public final class BackupService {
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(AppTime.ZONE);

    private final Database db;
    private final Clock clock;

    public BackupService(Database db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /** 例: tasktrack-backup-20261115-183000.db */
    public String defaultFileName() {
        return "tasktrack-backup-" + STAMP.format(clock.instant()) + ".db";
    }

    public Path databaseFile() {
        return db.file();
    }

    /** 復元直前のデータが退避されるファイル。 */
    public Path preRestoreFile() {
        return db.preRestoreFile();
    }

    /** 整合性の取れたスナップショットを {@code target} に作成する。 */
    public void backup(Path target) {
        try {
            db.backupTo(target);
        } catch (DataAccessException e) {
            throw new ValidationException(e.getMessage());
        }
    }

    /**
     * バックアップから復元する。現在のデータはすべて上書きされる
     * （直前の状態は {@link #preRestoreFile()} に退避される）。
     */
    public void restore(Path backup) {
        try {
            db.restoreFrom(backup);
        } catch (DataAccessException e) {
            throw new ValidationException(e.getMessage());
        }
    }
}
