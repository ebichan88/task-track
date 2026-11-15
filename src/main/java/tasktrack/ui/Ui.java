package tasktrack.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.table.DefaultTableModel;

import tasktrack.app.AppTime;
import tasktrack.app.ValidationException;

/** 画面共通の小さなユーティリティ。 */
final class Ui {
    static final int GAP = 8;

    private Ui() {
    }

    /** 操作を実行し、入力エラーは警告、想定外の例外はエラーとしてダイアログ表示する。成功したら true。 */
    static boolean run(Component parent, Runnable action) {
        try {
            action.run();
            return true;
        } catch (ValidationException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "確認してください", JOptionPane.WARNING_MESSAGE);
        } catch (RuntimeException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(parent, "エラーが発生しました。\n" + e.getMessage(), "エラー",
                    JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    static boolean confirm(Component parent, String message, String title) {
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    /** 「ラベル, 部品, ラベル, 部品, ...」を縦に並べたフォームを作る。 */
    static JPanel form(Object... labelsAndComponents) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        for (int i = 0; i < labelsAndComponents.length; i += 2) {
            c.gridy = i / 2;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.anchor = GridBagConstraints.NORTHWEST;
            panel.add(new JLabel((String) labelsAndComponents[i]), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            panel.add((JComponent) labelsAndComponents[i + 1], c);
        }
        return panel;
    }

    static <T extends JComponent> T padded(T component) {
        component.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        return component;
    }

    /** 編集不可・単一選択の表。 */
    static JTable table(String... columns) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(table.getRowHeight() + 6);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    static DefaultTableModel model(JTable table) {
        return (DefaultTableModel) table.getModel();
    }

    // --- 日付・日時入力（日本時間） ---------------------------------------------

    static JSpinner dateSpinner(LocalDate value) {
        return spinner(Date.from(AppTime.startOfDay(value)), "yyyy-MM-dd", Calendar.DAY_OF_MONTH);
    }

    static JSpinner dateTimeSpinner(Instant value) {
        return spinner(Date.from(value), "yyyy-MM-dd HH:mm:ss", Calendar.MINUTE);
    }

    private static JSpinner spinner(Date value, String pattern, int calendarField) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(value, null, null, calendarField));
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, pattern);
        editor.getFormat().setTimeZone(TimeZone.getTimeZone(AppTime.ZONE));
        spinner.setEditor(editor);
        // タイムゾーン設定前に描画済みのテキストを、日本時間の書式で表示し直す
        editor.getTextField().setValue(value);
        return spinner;
    }

    static LocalDate dateOf(JSpinner spinner) {
        return AppTime.dateOf(instantOf(spinner));
    }

    static Instant instantOf(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (java.text.ParseException e) {
            throw new ValidationException("日付・日時の形式が正しくありません。");
        }
        return ((Date) spinner.getValue()).toInstant();
    }

    static void setDate(JSpinner spinner, LocalDate date) {
        spinner.setValue(Date.from(AppTime.startOfDay(date)));
    }
}
