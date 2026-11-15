package tasktrack.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.plaf.basic.BasicHTML;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tasktrack.AppContext;

/** 利用者が入力した値が HTML として描画されない（＝画像読み込みなどの外部通信が起こらない）ことの確認。 */
class HtmlDisabledTest {
    private static final String HTML = "<html><img src=\"http://example.invalid/x.png\">作業";

    /** Swing が HTML として描画する場合、この client property に HTML ビューが設定される。 */
    private static Object htmlView(Component c) {
        return ((JComponent) c).getClientProperty(BasicHTML.propertyKey);
    }

    @Test
    void ordinaryLabelWouldRenderHtml() {
        // 前提の確認: 対策なしのラベルは HTML として解釈する
        assertNotNull(htmlView(new JLabel(HTML)));
    }

    @Test
    void plainLabelShowsTextAsIs() {
        JLabel label = Ui.plainLabel(HTML);
        assertNull(htmlView(label));
        assertEquals(HTML, label.getText());
        label.setText(HTML + "2");
        assertNull(htmlView(label));
    }

    @Test
    void dialogMessageDoesNotRenderHtmlOnAnyLine() {
        JComponent message = Ui.message("「作業\n" + HTML + "」を削除します。\r\n\n" + HTML);
        assertEquals(4, message.getComponentCount());
        for (Component line : message.getComponents()) {
            assertNull(htmlView(line));
        }
        assertEquals(HTML + "」を削除します。", ((JLabel) message.getComponent(1)).getText());
    }

    @Test
    void tableCellsDoNotRenderHtml() {
        JTable table = Ui.table("作業名", "件数");
        Ui.model(table).addRow(new Object[] {HTML, 1});
        for (int col = 0; col < 2; col++) {
            Component cell = table.prepareRenderer(table.getCellRenderer(0, col), 0, col);
            assertNull(htmlView(cell));
        }
        assertEquals(HTML, ((JLabel) table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0)).getText());
    }

    @Test
    void taskFormChoicesDoNotRenderHtml(@TempDir Path dir) {
        try (AppContext ctx = new AppContext(dir.resolve("t.db"), Clock.systemUTC())) {
            ctx.masters.createProject(HTML, "");
            ctx.masters.createCategory(HTML, "");
            ctx.masters.createTag(HTML);
            TaskForm form = new TaskForm(ctx.masters);

            List<Component> all = new ArrayList<>();
            collect(form, all);
            int checkedLists = 0;
            for (Component c : all) {
                if (c instanceof JComboBox<?> combo) {
                    checkedLists += assertRenderersPlain(new JList<>(combo.getModel()), combo.getRenderer());
                } else if (c instanceof JList<?> list) {
                    checkedLists += assertRenderersPlain(list, list.getCellRenderer());
                }
            }
            assertEquals(3, checkedLists, "プロジェクト・カテゴリ・タグの3つを検査する");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int assertRenderersPlain(JList list, javax.swing.ListCellRenderer renderer) {
        boolean sawHtmlValue = false;
        for (int i = 0; i < list.getModel().getSize(); i++) {
            Object value = list.getModel().getElementAt(i);
            sawHtmlValue |= value.toString().startsWith("<html>");
            assertNull(htmlView(renderer.getListCellRendererComponent(list, value, i, false, false)));
        }
        assertEquals(true, sawHtmlValue, "HTML 風の値が選択肢に含まれている");
        return 1;
    }

    private static void collect(Container parent, List<Component> out) {
        for (Component c : parent.getComponents()) {
            out.add(c);
            if (c instanceof Container child) {
                collect(child, out);
            }
        }
    }
}
