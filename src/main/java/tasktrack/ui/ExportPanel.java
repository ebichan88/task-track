package tasktrack.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;

import tasktrack.AppContext;
import tasktrack.db.AppStateRepository;
import tasktrack.app.AppTime;
import tasktrack.app.ExportService;
import tasktrack.app.ValidationException;

/** データ出力画面（指定期間の CSV を ZIP にまとめて出力）。 */
final class ExportPanel extends JPanel {
    private static final String LAST_DIR_KEY = AppStateRepository.LOCAL_PREFIX + "last_export_dir";
    private static final String[][] FILES = {
            {"time_entries.csv", "作業記録（期間内の時間部分のみ）"},
            {"daily_reviews.csv", "日次振り返り"},
            {"projects.csv", "プロジェクトマスタ（期間内の作業記録で使われているもの）"},
            {"categories.csv", "カテゴリマスタ（期間内の作業記録で使われているもの）"},
            {"tags.csv", "タグマスタ（期間内の作業記録で使われているもの）"},
            {"time_entry_tags.csv", "作業記録とタグの紐付け"},
    };

    private final AppContext ctx;
    private final JSpinner from;
    private final JSpinner to;
    private final JTextField directory = new JTextField(30);
    private final JLabel fileName = new JLabel();

    ExportPanel(AppContext ctx) {
        super(new BorderLayout(0, Ui.GAP));
        this.ctx = ctx;
        Ui.padded(this);

        // 初期値は先週（月〜日）
        LocalDate lastMonday = AppTime.today(ctx.clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);
        from = Ui.dateSpinner(lastMonday);
        to = Ui.dateSpinner(lastMonday.plusDays(6));
        from.addChangeListener(e -> updateFileName());
        to.addChangeListener(e -> updateFileName());
        directory.setText(ctx.appState.get(LAST_DIR_KEY).orElse(System.getProperty("user.home")));

        JPanel period = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        period.add(from);
        period.add(new JLabel("〜"));
        period.add(to);
        period.add(new JLabel("（開始日 0:00 から終了日 24:00 まで／日本時間）"));

        JButton browse = new JButton("参照...");
        browse.addActionListener(e -> browse());
        JPanel dirPanel = new JPanel(new BorderLayout(4, 0));
        dirPanel.add(directory, BorderLayout.CENTER);
        dirPanel.add(browse, BorderLayout.EAST);

        JTable files = Ui.table("ファイル名", "内容");
        for (String[] f : FILES) {
            Ui.model(files).addRow(f);
        }
        files.setRowSelectionAllowed(false);
        files.getColumnModel().getColumn(0).setPreferredWidth(160);
        files.getColumnModel().getColumn(1).setPreferredWidth(480);
        JScrollPane fileScroll = new JScrollPane(files);
        fileScroll.setBorder(BorderFactory.createTitledBorder("2. 出力対象ファイル（ZIP に格納されます）"));

        JPanel north = new JPanel(new BorderLayout());
        JPanel periodBox = new JPanel(new BorderLayout());
        periodBox.setBorder(BorderFactory.createTitledBorder("1. 出力対象期間"));
        periodBox.add(period, BorderLayout.CENTER);
        north.add(periodBox, BorderLayout.CENTER);

        JButton export = new JButton("ZIP ファイルを出力");
        export.addActionListener(e -> export());
        JPanel exportRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        exportRow.add(export);

        JPanel dest = Ui.form("出力先フォルダ", dirPanel, "ファイル名", fileName);
        dest.setBorder(BorderFactory.createTitledBorder("3. 出力先"));
        JPanel south = new JPanel(new BorderLayout(0, Ui.GAP));
        south.add(dest, BorderLayout.NORTH);
        south.add(exportRow, BorderLayout.CENTER);
        south.add(new JLabel("<html>※ 登録内容は匿名化されず、そのまま出力されます。外部ツール（ChatGPT など）へアップロードする場合は、"
                + "出力内容を確認し、会社の情報管理ルールに従って判断してください。<br>"
                + "※ 本ツールが外部へデータを送信することはありません。計測中の作業は出力されません。</html>"),
                BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(fileScroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        updateFileName();
    }

    private void updateFileName() {
        try {
            fileName.setText(ExportService.defaultFileName(Ui.dateOf(from), Ui.dateOf(to)));
        } catch (ValidationException e) {
            fileName.setText("");
        }
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser(directory.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("出力先フォルダの選択");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            directory.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void export() {
        Ui.run(this, () -> {
            LocalDate fromDate = Ui.dateOf(from);
            LocalDate toDate = Ui.dateOf(to);
            if (toDate.isBefore(fromDate)) {
                throw new ValidationException("終了日は開始日以降を指定してください。");
            }
            String dirText = directory.getText().strip();
            if (dirText.isEmpty() || !Files.isDirectory(Path.of(dirText))) {
                throw new ValidationException("出力先フォルダが存在しません。");
            }
            File target = new File(dirText, ExportService.defaultFileName(fromDate, toDate));
            if (target.exists() && !Ui.confirm(this, "同名のファイルが既にあります。上書きしますか？\n" + target,
                    "上書きの確認")) {
                return;
            }
            ctx.export.export(fromDate, toDate, target.toPath());
            ctx.appState.put(LAST_DIR_KEY, dirText);
            JOptionPane.showMessageDialog(this, Ui.message("出力しました。\n" + target), "データ出力",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }
}
