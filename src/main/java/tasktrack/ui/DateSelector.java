package tasktrack.ui;

import java.awt.FlowLayout;
import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;

import tasktrack.app.AppTime;

/** 日付の選択（前日・翌日・今日ボタン付き）。 */
final class DateSelector extends JPanel {
    private final JSpinner spinner;

    DateSelector(Clock clock, Consumer<LocalDate> onChange) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        spinner = Ui.dateSpinner(AppTime.today(clock));
        JButton prev = Theme.button("◀ 前日");
        JButton next = Theme.button("翌日 ▶");
        JButton today = Theme.button("今日");
        prev.addActionListener(e -> Ui.setDate(spinner, getDate().minusDays(1)));
        next.addActionListener(e -> Ui.setDate(spinner, getDate().plusDays(1)));
        today.addActionListener(e -> Ui.setDate(spinner, AppTime.today(clock)));
        spinner.addChangeListener(e -> onChange.accept(getDate()));
        add(new JLabel("日付:"));
        add(prev);
        add(spinner);
        add(next);
        add(today);
    }

    LocalDate getDate() {
        return AppTime.dateOf(((java.util.Date) spinner.getValue()).toInstant());
    }
}
