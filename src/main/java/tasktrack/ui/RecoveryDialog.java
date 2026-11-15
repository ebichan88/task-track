package tasktrack.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;

import tasktrack.AppContext;
import tasktrack.app.AppTime;
import tasktrack.domain.TimeEntry;

/**
 * 前回終了時に終了日時が保存されなかった作業記録（異常終了など）への対応を確認する。
 * 対応を決めるまで閉じられない。
 */
final class RecoveryDialog extends JDialog {

    private RecoveryDialog(Window owner, AppContext ctx, TimeEntry entry) {
        super(owner, "未終了の作業記録があります", ModalityType.APPLICATION_MODAL);
        Names names = new Names(ctx.masters);
        String project = names.project(entry.projectId());

        JPanel info = Ui.form(
                "作業名", new JLabel(entry.taskName()),
                "プロジェクト", new JLabel(project.isEmpty() ? "（なし）" : project),
                "カテゴリ", new JLabel(names.category(entry.categoryId())),
                "開始日時", new JLabel(AppTime.formatDateTime(entry.startAt())));

        JRadioButton now = new JRadioButton("現在時刻で終了する", true);
        JRadioButton manual = new JRadioButton("終了日時を手動で指定する:");
        JRadioButton discard = new JRadioButton("この記録を破棄する（削除）");
        ButtonGroup group = new ButtonGroup();
        group.add(now);
        group.add(manual);
        group.add(discard);
        JSpinner endAt = Ui.dateTimeSpinner(entry.startAt());
        endAt.setEnabled(false);
        Runnable sync = () -> endAt.setEnabled(manual.isSelected());
        now.addActionListener(e -> sync.run());
        manual.addActionListener(e -> sync.run());
        discard.addActionListener(e -> sync.run());

        JPanel manualRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        manualRow.add(manual);
        manualRow.add(endAt);
        JPanel options = new JPanel(new GridLayout(0, 1, 0, 2));
        options.add(now);
        options.add(manualRow);
        options.add(discard);

        JButton ok = new JButton("決定");
        ok.addActionListener(e -> {
            boolean done = Ui.run(this, () -> {
                if (now.isSelected()) {
                    ctx.timer.finishNow(entry.id());
                } else if (manual.isSelected()) {
                    ctx.timer.finishAt(entry.id(), Ui.instantOf(endAt));
                } else if (Ui.confirm(this, "この作業記録を削除します。よろしいですか？", "破棄の確認")) {
                    ctx.timer.discard(entry.id());
                } else {
                    throw new tasktrack.app.ValidationException("対応方法を選び直してください。");
                }
            });
            if (done) {
                dispose();
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);

        JPanel center = new JPanel(new BorderLayout(0, Ui.GAP));
        center.add(info, BorderLayout.NORTH);
        center.add(options, BorderLayout.CENTER);

        JPanel content = Ui.padded(new JPanel(new BorderLayout(0, Ui.GAP)));
        content.add(new JLabel("<html>前回、終了日時が保存されないまま終了した作業記録が見つかりました。<br>"
                + "この記録の扱いを選んでください。</html>"), BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    /** 未終了の作業記録が無くなるまで、古いものから順に対応を確認する。1件でも処理したら true。 */
    static boolean resolveAll(Window owner, AppContext ctx) {
        boolean any = false;
        for (TimeEntry entry : ctx.timer.findUnfinished()) {
            new RecoveryDialog(owner, ctx, entry).setVisible(true);
            any = true;
        }
        return any;
    }
}
