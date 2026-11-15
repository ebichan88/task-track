package tasktrack.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import tasktrack.AppContext;
import tasktrack.app.AppTime;
import tasktrack.domain.TimeEntry;

/** 作業記録一覧（日付別の作業履歴）。手動登録・編集・削除・再開の起点。 */
final class EntriesPanel extends JPanel {
    private final AppContext ctx;
    private final UiEvents events;
    private final DateSelector dateSelector;
    private final JTable table = Ui.table("作業名", "プロジェクト", "カテゴリ", "開始", "終了", "作業時間", "登録方法", "タグ");
    private final JLabel total = new JLabel();
    private final JButton editButton = new JButton("編集...");
    private final JButton deleteButton = new JButton("削除");
    private final JButton resumeButton = new JButton("▶ この作業を再開");
    private List<TimeEntry> rows = List.of();

    EntriesPanel(AppContext ctx, UiEvents events) {
        super(new BorderLayout(0, Ui.GAP));
        this.ctx = ctx;
        this.events = events;
        Ui.padded(this);
        dateSelector = new DateSelector(ctx.clock, d -> reload());

        JButton addButton = new JButton("手動登録...");
        addButton.addActionListener(e -> create());
        editButton.addActionListener(e -> edit());
        deleteButton.addActionListener(e -> delete());
        resumeButton.addActionListener(e -> resume());
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    edit();
                }
            }
        });
        table.getColumnModel().getColumn(0).setPreferredWidth(220);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(resumeButton);
        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.WEST);
        south.add(total, BorderLayout.EAST);

        add(dateSelector, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        events.onDataChanged(this::reload);
        reload();
    }

    private void reload() {
        LocalDate date = dateSelector.getDate();
        String selectedId = selected() == null ? null : selected().id();
        rows = ctx.entries.listByDate(date);
        Names names = new Names(ctx.masters);
        Instant now = ctx.clock.instant();
        Ui.model(table).setRowCount(0);
        long totalSeconds = 0;
        for (TimeEntry e : rows) {
            totalSeconds += ctx.entries.secondsWithin(e, date);
            Ui.model(table).addRow(new Object[] {
                    e.taskName(), names.project(e.projectId()), names.category(e.categoryId()),
                    AppTime.formatTimeOn(date, e.startAt()),
                    e.running() ? "（計測中）" : AppTime.formatTimeOn(date, e.endAt()),
                    AppTime.formatDuration(e.duration(now).toSeconds()),
                    e.manual() ? "手動" : "タイマー", names.tags(e.tagIds())});
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(selectedId)) {
                table.setRowSelectionInterval(i, i);
            }
        }
        total.setText(rows.size() + " 件 / この日の作業時間 合計 " + AppTime.formatDuration(totalSeconds));
        updateButtons();
    }

    private TimeEntry selected() {
        int row = table.getSelectedRow();
        return row >= 0 && row < rows.size() ? rows.get(row) : null;
    }

    private void updateButtons() {
        boolean has = selected() != null;
        editButton.setEnabled(has);
        deleteButton.setEnabled(has);
        resumeButton.setEnabled(has && !selected().running());
    }

    private void create() {
        // 表示中の日付の、現在時刻の1時間前を初期値にする
        LocalTime time = LocalTime.ofInstant(ctx.clock.instant(), AppTime.ZONE).minusHours(1);
        Instant defaultStart = dateSelector.getDate().atTime(time).atZone(AppTime.ZONE).toInstant();
        if (EntryDialog.showCreate(this, ctx, defaultStart)) {
            events.fireDataChanged();
        }
    }

    private void edit() {
        TimeEntry entry = selected();
        if (entry != null && EntryDialog.showEdit(this, ctx, entry)) {
            events.fireDataChanged();
        }
    }

    private void delete() {
        TimeEntry entry = selected();
        if (entry != null && Ui.confirm(this, "「" + entry.taskName() + "」を削除します。よろしいですか？", "削除の確認")
                && Ui.run(this, () -> ctx.entries.delete(entry.id()))) {
            events.fireDataChanged();
        }
    }

    private void resume() {
        TimeEntry entry = selected();
        if (entry != null && Ui.run(this, () -> ctx.timer.resume(entry.id()))) {
            events.fireDataChanged();
        }
    }
}
