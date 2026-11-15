package tasktrack.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import tasktrack.AppContext;
import tasktrack.app.AppTime;
import tasktrack.app.ReviewInput;
import tasktrack.domain.DailyReview;

/** 日次振り返りの登録・編集（1日1件、全項目任意）。 */
final class ReviewPanel extends JPanel {
    private final AppContext ctx;
    private final DateSelector dateSelector;
    private final JTextArea achievements = area();
    private final JTextArea blockers = area();
    private final JTextArea decisions = area();
    private final JTextArea nextActions = area();
    private final JTextArea reflections = area();
    private final JLabel state = new JLabel(" ");

    /** 現在入力欄に表示している日付と、その読み込み時点の内容。 */
    private LocalDate shownDate;
    private ReviewInput loaded = new ReviewInput("", "", "", "", "");

    ReviewPanel(AppContext ctx, UiEvents events) {
        super(new BorderLayout(0, Ui.GAP));
        this.ctx = ctx;
        Ui.padded(this);
        dateSelector = new DateSelector(ctx.clock, this::dateChanged);

        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 4));
        fields.add(section("成果・アウトプット（達成したこと、作成した成果物）", achievements));
        fields.add(section("課題・阻害要因（作業を妨げた要因、困ったこと）", blockers));
        fields.add(section("技術的判断・意思決定（技術選定や設計上の判断、その理由）", decisions));
        fields.add(section("次のアクション（翌日以降に実施すること）", nextActions));
        fields.add(section("所感・気づき（感じたこと、改善点など）", reflections));

        JButton save = new JButton("保存");
        save.addActionListener(e -> {
            if (save()) {
                state.setText(state.getText() + "  ✔ 保存しました");
            }
        });
        JPanel south = new JPanel(new BorderLayout());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(save);
        south.add(state, BorderLayout.WEST);
        south.add(right, BorderLayout.EAST);

        add(dateSelector, BorderLayout.NORTH);
        add(fields, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        // 復元などで DB が入れ替わった場合、未編集なら読み直す
        events.onDataChanged(() -> {
            if (!isDirty()) {
                load(shownDate);
            }
        });
        load(dateSelector.getDate());
    }

    private static JTextArea area() {
        JTextArea a = new JTextArea(3, 40);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        return a;
    }

    private static JPanel section(String title, JTextArea area) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        return p;
    }

    private ReviewInput input() {
        return new ReviewInput(achievements.getText(), blockers.getText(), decisions.getText(),
                nextActions.getText(), reflections.getText());
    }

    boolean isDirty() {
        return !input().equals(loaded);
    }

    /** 未保存の変更があれば保存するか確認する（日付切り替え時・アプリ終了時）。 */
    void confirmSaveIfDirty() {
        if (isDirty() && JOptionPane.showConfirmDialog(this,
                shownDate + " の振り返りに未保存の変更があります。保存しますか？", "日次振り返り",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
            save();
        }
    }

    private void dateChanged(LocalDate date) {
        if (date.equals(shownDate)) {
            return;
        }
        confirmSaveIfDirty();
        load(date);
    }

    private void load(LocalDate date) {
        shownDate = date;
        Optional<DailyReview> review = ctx.reviews.find(date);
        achievements.setText(review.map(DailyReview::achievements).orElse(""));
        blockers.setText(review.map(DailyReview::blockers).orElse(""));
        decisions.setText(review.map(DailyReview::decisions).orElse(""));
        nextActions.setText(review.map(DailyReview::nextActions).orElse(""));
        reflections.setText(review.map(DailyReview::reflections).orElse(""));
        achievements.setCaretPosition(0);
        loaded = input();
        showState(review);
    }

    private boolean save() {
        return Ui.run(this, () -> {
            Optional<DailyReview> saved = ctx.reviews.save(shownDate, input());
            loaded = input();
            showState(saved);
        });
    }

    private void showState(Optional<DailyReview> review) {
        state.setText(review.map(r -> shownDate + " の振り返り: 登録済み（最終更新 "
                + AppTime.formatDateTime(r.updatedAt()) + "）").orElse(shownDate + " の振り返り: 未登録"));
    }
}
