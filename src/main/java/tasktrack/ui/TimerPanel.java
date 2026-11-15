package tasktrack.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Duration;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import tasktrack.AppContext;
import tasktrack.app.AppTime;
import tasktrack.domain.TimeEntry;

/** メイン画面のタイマー部分。計測中の作業を常時表示し、開始・切り替え・停止を行う。 */
final class TimerPanel extends JPanel {
    private static final Color RUNNING_COLOR = new Color(0x0B6E4F);

    private final AppContext ctx;
    private final UiEvents events;
    private final TaskForm form;

    private final JLabel status = new JLabel();
    private final JLabel elapsed = new JLabel();
    private final JLabel taskName = Ui.plainLabel("");
    private final JLabel project = Ui.plainLabel("");
    private final JLabel category = Ui.plainLabel("");
    private final JLabel startedAt = new JLabel();
    private final JButton stopButton = Theme.button("■ 停止");
    private final JButton startButton = Theme.heroButton("");

    /** 表示用に保持している計測中の作業。経過時間はここから毎秒計算する（DB は読まない）。 */
    private TimeEntry current;

    TimerPanel(AppContext ctx, UiEvents events) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.events = events;
        this.form = new TaskForm(ctx.masters);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 4));
        setPreferredSize(new Dimension(372, 100));

        add(buildCurrentPanel(), BorderLayout.NORTH);
        add(buildStartPanel(), BorderLayout.CENTER);

        stopButton.addActionListener(e -> {
            if (Ui.run(this, ctx.timer::stop)) {
                events.fireDataChanged();
            }
        });
        startButton.addActionListener(e -> {
            if (Ui.run(this, () -> ctx.timer.start(form.getSpec()))) {
                form.clearTask();
                events.fireDataChanged();
            }
        });

        events.onDataChanged(this::reload);
        new Timer(1000, e -> updateElapsed()).start();
        reload();
    }

    private JPanel buildCurrentPanel() {
        elapsed.setFont(elapsed.getFont().deriveFont(Font.BOLD, 34f));
        status.setFont(status.getFont().deriveFont(Font.BOLD));
        taskName.setFont(taskName.getFont().deriveFont(Font.BOLD, 15f));

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        for (JLabel label : new JLabel[] {status, elapsed, taskName, project, category, startedAt}) {
            label.setAlignmentX(LEFT_ALIGNMENT);
            details.add(label);
            details.add(Box.createVerticalStrut(4));
        }

        JPanel body = new JPanel(new BorderLayout(0, Ui.GAP));
        body.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 2));
        body.add(details, BorderLayout.CENTER);
        body.add(stopButton, BorderLayout.SOUTH);
        JPanel card = Theme.card(new BorderLayout());
        card.add(Theme.section("計測中の作業", body), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildStartPanel() {
        JPanel body = new JPanel(new BorderLayout(0, Ui.GAP));
        body.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        body.add(form, BorderLayout.NORTH);
        JPanel south = new JPanel(new BorderLayout());
        south.add(startButton, BorderLayout.NORTH);
        body.add(south, BorderLayout.CENTER);
        JPanel card = Theme.card(new BorderLayout());
        card.add(Theme.section("作業を開始", body), BorderLayout.CENTER);
        return card;
    }

    /** 入力欄に作業内容をセットする（履歴からの転記用）。 */
    TaskForm form() {
        return form;
    }

    private void reload() {
        form.reloadMasters();
        Optional<TimeEntry> running = ctx.timer.current();
        current = running.orElse(null);
        if (current == null) {
            status.setText("計測していません");
            status.setForeground(Theme.MUTED_TEXT);
            elapsed.setForeground(Theme.MUTED_TEXT);
            taskName.setText("―");
            project.setText("プロジェクト: ―");
            category.setText("カテゴリ: ―");
            startedAt.setText("開始: ―");
            startButton.setText("▶ 開始");
        } else {
            Names names = new Names(ctx.masters);
            status.setText("● 計測中");
            status.setForeground(RUNNING_COLOR);
            elapsed.setForeground(RUNNING_COLOR);
            taskName.setText(current.taskName());
            String projectName = names.project(current.projectId());
            project.setText("プロジェクト: " + (projectName.isEmpty() ? "（なし）" : projectName));
            category.setText("カテゴリ: " + names.category(current.categoryId()));
            startedAt.setText("開始: " + AppTime.formatDateTime(current.startAt()));
            startButton.setText("▶ この作業に切り替えて開始");
        }
        stopButton.setEnabled(current != null);
        updateElapsed();
    }

    private void updateElapsed() {
        Duration d = current == null ? Duration.ZERO : current.duration(ctx.clock.instant());
        elapsed.setText(AppTime.formatDuration(d.toSeconds()));
    }
}
