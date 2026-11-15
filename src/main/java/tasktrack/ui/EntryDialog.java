package tasktrack.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

import tasktrack.AppContext;
import tasktrack.app.AppTime;
import tasktrack.app.EntryInput;
import tasktrack.app.TaskSpec;
import tasktrack.domain.TimeEntry;

/** 作業記録編集画面（手動登録・編集・削除）。 */
final class EntryDialog extends JDialog {
    private final AppContext ctx;
    private final TimeEntry editing;
    private final TaskForm form;
    private final JSpinner start;
    private final JSpinner end;
    private final JCheckBox stillRunning = new JCheckBox("計測中（終了日時を設定しない）");
    private boolean changed;

    private EntryDialog(Window owner, AppContext ctx, TimeEntry editing, Instant defaultStart, Instant defaultEnd) {
        super(owner, editing == null ? "作業記録の手動登録" : "作業記録の編集", ModalityType.APPLICATION_MODAL);
        this.ctx = ctx;
        this.editing = editing;
        this.form = new TaskForm(ctx.masters);
        this.start = Ui.dateTimeSpinner(defaultStart);
        this.end = Ui.dateTimeSpinner(defaultEnd);

        boolean running = editing != null && editing.running();
        if (editing != null) {
            form.setSpec(TaskSpec.of(editing));
        }
        stillRunning.setSelected(running);
        stillRunning.setVisible(running);
        end.setEnabled(!running);
        stillRunning.addActionListener(e -> end.setEnabled(!stillRunning.isSelected()));

        JPanel endPanel = new JPanel(new BorderLayout(Ui.GAP, 0));
        endPanel.add(end, BorderLayout.CENTER);
        endPanel.add(stillRunning, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout());
        center.add(form, BorderLayout.CENTER);
        String method = editing == null || editing.manual() ? "手動登録" : "タイマー登録";
        center.add(Ui.form("開始日時 *", start, "終了日時 *", endPanel, "登録方法", new JLabel(method)),
                BorderLayout.SOUTH);

        JButton save = Theme.primaryButton(editing == null ? "登録" : "保存");
        JButton cancel = Theme.button("キャンセル");
        JButton delete = Theme.button("削除");
        save.addActionListener(e -> save());
        cancel.addActionListener(e -> dispose());
        delete.addActionListener(e -> delete());
        delete.setVisible(editing != null);

        JPanel buttons = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(delete);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(save);
        right.add(cancel);
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        JPanel content = Ui.padded(Theme.gradientPanel(new BorderLayout(0, Ui.GAP)));
        content.add(center, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(Theme.clear(content));
        getRootPane().setDefaultButton(save);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setSize(Math.max(getWidth(), 520), getHeight());
        setLocationRelativeTo(owner);
    }

    /** 手動登録ダイアログを開く。登録されたら true。 */
    static boolean showCreate(Component parent, AppContext ctx, Instant defaultStart) {
        Instant s = defaultStart.truncatedTo(ChronoUnit.MINUTES);
        return show(new EntryDialog(SwingUtilities.getWindowAncestor(parent), ctx, null, s, s.plus(1, ChronoUnit.HOURS)));
    }

    /** 編集ダイアログを開く。保存または削除されたら true。 */
    static boolean showEdit(Component parent, AppContext ctx, TimeEntry entry) {
        Instant endDefault = entry.endAt() != null ? entry.endAt() : AppTime.now(ctx.clock);
        return show(new EntryDialog(SwingUtilities.getWindowAncestor(parent), ctx, entry, entry.startAt(), endDefault));
    }

    private static boolean show(EntryDialog dialog) {
        dialog.form.focusTaskName();
        dialog.setVisible(true);
        return dialog.changed;
    }

    private void save() {
        boolean ok = Ui.run(this, () -> {
            Instant endAt = stillRunning.isSelected() ? null : Ui.instantOf(end);
            EntryInput input = new EntryInput(form.getSpec(), Ui.instantOf(start), endAt);
            if (editing == null) {
                ctx.entries.createManual(input);
            } else {
                ctx.entries.update(editing.id(), input);
            }
        });
        if (ok) {
            changed = true;
            dispose();
        }
    }

    private void delete() {
        if (Ui.confirm(this, "この作業記録を削除します。よろしいですか？", "削除の確認")
                && Ui.run(this, () -> ctx.entries.delete(editing.id()))) {
            changed = true;
            dispose();
        }
    }
}
