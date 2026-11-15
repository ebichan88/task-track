package tasktrack;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import tasktrack.ui.MainFrame;

/** アプリケーションのエントリポイント。 */
public final class Main {
    private static final String DB_FILE_NAME = "tasktrack.db";

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::start);
    }

    /**
     * データ保存先。優先順: システムプロパティ tasktrack.dataDir → %APPDATA%\TaskTrack（Windows）
     * → ~/.tasktrack（その他）。
     */
    static Path dataDir() {
        String override = System.getProperty("tasktrack.dataDir");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "TaskTrack");
        }
        return Path.of(System.getProperty("user.home"), ".tasktrack");
    }

    private static void start() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
            // 既定の Look&Feel のまま続行する
        }

        Path dir = dataDir();
        FileChannel lockChannel;
        AppContext ctx;
        try {
            Files.createDirectories(dir);
            // 二重起動防止（同じ DB を2つのプロセスから操作しない）
            lockChannel = FileChannel.open(dir.resolve("tasktrack.lock"), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            FileLock lock = lockChannel.tryLock();
            if (lock == null) {
                lockChannel.close();
                JOptionPane.showMessageDialog(null, "TaskTrack は既に起動しています。", "TaskTrack",
                        JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
                return;
            }
            ctx = new AppContext(dir.resolve(DB_FILE_NAME), Clock.systemDefaultZone());
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "起動できませんでした。\n" + e.getMessage() + "\n\nデータ保存先: " + dir,
                    "TaskTrack", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        MainFrame frame = new MainFrame(ctx, () -> {
            ctx.close();
            try {
                lockChannel.close();
            } catch (IOException ignored) {
                // 終了時のロック解放失敗は無視してよい（プロセス終了で解放される）
            }
            System.exit(0);
        });
        frame.setVisible(true);
        frame.checkUnfinishedEntries();
    }
}
