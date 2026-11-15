package tasktrack.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;

import tasktrack.AppContext;
import tasktrack.app.AppTime;
import tasktrack.app.Summary;

/** 集計画面（日別・週別・カテゴリ別・プロジェクト別）。 */
final class SummaryPanel extends JPanel {
    private final AppContext ctx;
    private final JSpinner from;
    private final JSpinner to;
    private final JLabel total = new JLabel(" ");
    private final JTable daily = Ui.table("日付", "作業時間", "時間（h）", "件数");
    private final JTable weekly = Ui.table("週（月曜始まり）", "作業時間", "時間（h）", "件数");
    private final JTable byCategory = Ui.table("カテゴリ", "作業時間", "時間（h）", "件数", "割合");
    private final JTable byProject = Ui.table("プロジェクト", "作業時間", "時間（h）", "件数", "割合");

    SummaryPanel(AppContext ctx, UiEvents events) {
        super(new BorderLayout(0, Ui.GAP));
        this.ctx = ctx;
        Ui.padded(this);
        LocalDate today = AppTime.today(ctx.clock);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        from = Ui.dateSpinner(monday);
        to = Ui.dateSpinner(monday.plusDays(6));

        JButton run = new JButton("集計");
        run.addActionListener(e -> reload(true));
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        north.add(new JLabel("期間:"));
        north.add(from);
        north.add(new JLabel("〜"));
        north.add(to);
        north.add(run);
        north.add(rangeButton("今週", 0, false));
        north.add(rangeButton("先週", -1, false));
        north.add(rangeButton("今月", 0, true));
        north.add(rangeButton("先月", -1, true));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("日別", new JScrollPane(daily));
        tabs.addTab("週別", new JScrollPane(weekly));
        tabs.addTab("カテゴリ別", new JScrollPane(byCategory));
        tabs.addTab("プロジェクト別", new JScrollPane(byProject));

        JPanel center = new JPanel(new BorderLayout(0, Ui.GAP));
        total.setFont(total.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        center.add(total, BorderLayout.NORTH);
        center.add(tabs, BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(new JLabel("※ 日付の境界は日本時間 0:00。計測中の作業は集計に含まれません。"), BorderLayout.SOUTH);

        events.onDataChanged(() -> reload(false));
        reload(false);
    }

    private JButton rangeButton(String label, int offset, boolean month) {
        JButton button = new JButton(label);
        button.addActionListener(e -> {
            LocalDate today = AppTime.today(ctx.clock);
            LocalDate start;
            LocalDate end;
            if (month) {
                start = today.withDayOfMonth(1).plusMonths(offset);
                end = start.with(TemporalAdjusters.lastDayOfMonth());
            } else {
                start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset);
                end = start.plusDays(6);
            }
            Ui.setDate(from, start);
            Ui.setDate(to, end);
            reload(true);
        });
        return button;
    }

    /** @param interactive false の場合（他画面の変更に伴う自動更新）は入力エラーを表示しない */
    private void reload(boolean interactive) {
        Runnable action = () -> show(ctx.summary.summarize(Ui.dateOf(from), Ui.dateOf(to)));
        if (interactive) {
            Ui.run(this, action);
        } else {
            try {
                action.run();
            } catch (tasktrack.app.ValidationException ignored) {
                // 入力途中の期間は、次に「集計」を押したときに案内する
            }
        }
    }

    private void show(Summary s) {
        total.setText("合計 " + AppTime.formatDuration(s.totalSeconds()) + "（" + hours(s.totalSeconds())
                + " h） / 作業記録 " + s.entryCount() + " 件");
        fill(daily, s.daily(), -1);
        fill(weekly, s.weekly(), -1);
        fill(byCategory, s.byCategory(), s.totalSeconds());
        fill(byProject, s.byProject(), s.totalSeconds());
    }

    private static void fill(JTable table, List<Summary.Row> rows, long totalSeconds) {
        Ui.model(table).setRowCount(0);
        for (Summary.Row r : rows) {
            if (totalSeconds < 0) {
                Ui.model(table).addRow(new Object[] {r.label(), AppTime.formatDuration(r.seconds()),
                        hours(r.seconds()), r.entryCount()});
            } else {
                String ratio = totalSeconds == 0 ? "-" : String.format("%.1f%%", r.seconds() * 100.0 / totalSeconds);
                Ui.model(table).addRow(new Object[] {r.label(), AppTime.formatDuration(r.seconds()),
                        hours(r.seconds()), r.entryCount(), ratio});
            }
        }
    }

    private static String hours(long seconds) {
        return String.format("%.2f", seconds / 3600.0);
    }
}
