package tasktrack.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import tasktrack.AppContext;

/** バックアップ画面（データベースのバックアップ・復元）。 */
final class BackupPanel extends JPanel {
    private static final String LAST_DIR_KEY = "last_backup_dir";

    private final AppContext ctx;
    private final Runnable afterRestore;

    /** @param afterRestore 復元後の後処理（全画面の再読み込み、未終了記録の確認） */
    BackupPanel(AppContext ctx, Runnable afterRestore) {
        super(new BorderLayout());
        this.ctx = ctx;
        this.afterRestore = afterRestore;
        Ui.padded(this);

        JTextField dbPath = new JTextField(ctx.backup.databaseFile().toString());
        dbPath.setEditable(false);

        JButton backup = new JButton("バックアップを作成...");
        backup.addActionListener(e -> backup());
        JPanel backupBox = box("バックアップ", backup,
                "現在のデータベースの内容を、指定したファイルへ保存します。アプリの利用中でも整合性の取れた状態で保存されます。");

        JButton restore = new JButton("バックアップから復元...");
        restore.addActionListener(e -> restore());
        JPanel restoreBox = box("復元", restore,
                "バックアップファイルの内容でデータベースを置き換えます。<b>現在のデータはすべて上書きされます。</b>");

        JPanel content = new JPanel(new GridLayout(0, 1, 0, Ui.GAP));
        content.add(Ui.form("データベースの場所", dbPath));
        content.add(backupBox);
        content.add(restoreBox);
        add(content, BorderLayout.NORTH);
    }

    private static JPanel box(String title, JButton button, String html) {
        JPanel panel = new JPanel(new BorderLayout(0, Ui.GAP));
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)));
        panel.add(new JLabel("<html>" + html + "</html>"), BorderLayout.CENTER);
        JPanel row = new JPanel(new BorderLayout());
        row.add(button, BorderLayout.WEST);
        panel.add(row, BorderLayout.SOUTH);
        return panel;
    }

    private JFileChooser chooser(String title) {
        JFileChooser chooser = new JFileChooser(
                ctx.appState.get(LAST_DIR_KEY).orElse(System.getProperty("user.home")));
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("TaskTrack バックアップ (*.db)", "db"));
        return chooser;
    }

    private void backup() {
        JFileChooser chooser = chooser("バックアップの保存先");
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), ctx.backup.defaultFileName()));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selected = chooser.getSelectedFile();
        File target = selected.getName().contains(".") ? selected : new File(selected.getPath() + ".db");
        if (target.exists() && !Ui.confirm(this, "同名のファイルが既にあります。上書きしますか？\n" + target, "上書きの確認")) {
            return;
        }
        if (Ui.run(this, () -> ctx.backup.backup(target.toPath()))) {
            ctx.appState.put(LAST_DIR_KEY, target.getParent());
            JOptionPane.showMessageDialog(this, "バックアップを作成しました。\n" + target, "バックアップ",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void restore() {
        JFileChooser chooser = chooser("復元するバックアップファイルの選択");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File source = chooser.getSelectedFile();
        String warning = "バックアップから復元すると、現在のデータ（作業記録・振り返り・マスタ・計測中の作業）は\n"
                + "すべてバックアップの内容で上書きされます。この操作は元に戻せません。\n\n"
                + "復元元: " + source + "\n\n復元を実行しますか？";
        Object[] options = {"上書きして復元する", "キャンセル"};
        if (JOptionPane.showOptionDialog(this, warning, "復元の確認", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[1]) != 0) {
            return;
        }
        if (Ui.run(this, () -> ctx.backup.restore(source.toPath()))) {
            JOptionPane.showMessageDialog(this, "復元しました。\n（復元直前のデータは次のファイルに退避されています）\n"
                    + ctx.backup.preRestoreFile(), "復元", JOptionPane.INFORMATION_MESSAGE);
        }
        // 失敗時も DB 接続は張り直されているため、常に画面を読み直す
        afterRestore.run();
    }
}
