package tasktrack.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;

import tasktrack.app.MasterService;
import tasktrack.app.TaskSpec;
import tasktrack.domain.MasterItem;
import tasktrack.domain.Tag;

/** 作業内容（プロジェクト・カテゴリ・作業名・作業内容・タグ）の入力欄。タイマー開始と作業記録編集で共用する。 */
final class TaskForm extends JPanel {

    /** コンボボックス・リストの選択肢。 */
    record Option(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final Option NO_PROJECT = new Option(null, "（なし）");

    private final MasterService masters;
    private final JComboBox<Option> project = new JComboBox<>();
    private final JComboBox<Option> category = new JComboBox<>();
    private final JTextField taskName = new JTextField(20);
    private final JTextArea description = new JTextArea(3, 20);
    private final DefaultListModel<Option> tagModel = new DefaultListModel<>();
    private final JList<Option> tags = new JList<>(tagModel);

    TaskForm(MasterService masters) {
        this.masters = masters;
        setLayout(new java.awt.BorderLayout());
        project.setRenderer(Ui.noHtml(new DefaultListCellRenderer()));
        category.setRenderer(Ui.noHtml(new DefaultListCellRenderer()));

        description.setLineWrap(true);
        description.setWrapStyleWord(true);

        // クリックで選択をトグルするチェックボックス風リスト
        tags.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                if (isSelectedIndex(index0)) {
                    removeSelectionInterval(index0, index1);
                } else {
                    addSelectionInterval(index0, index1);
                }
            }
        });
        tags.setCellRenderer(new TagRenderer());
        tags.setVisibleRowCount(4);
        JScrollPane tagScroll = new JScrollPane(tags);
        tagScroll.setPreferredSize(new Dimension(100, 84));

        add(Ui.form(
                "プロジェクト", project,
                "カテゴリ *", category,
                "作業名 *", taskName,
                "作業内容", new JScrollPane(description),
                "タグ", tagScroll), java.awt.BorderLayout.CENTER);
        reloadMasters();
    }

    /** マスタの変更を選択肢へ反映する（現在の選択は維持する）。 */
    void reloadMasters() {
        TaskSpec current = getSpec();
        fill(project, NO_PROJECT, masters.projects(), current.projectId());
        fill(category, null, masters.categories(), current.categoryId());
        tagModel.clear();
        for (Tag t : masters.tags()) {
            tagModel.addElement(new Option(t.id(), t.name()));
        }
        selectTags(current.tagIds());
    }

    /** 有効なマスタを選択肢にする。選択中のものは無効でも選択肢に残す。 */
    private static void fill(JComboBox<Option> combo, Option first, List<? extends MasterItem> items,
                             String selectedId) {
        DefaultComboBoxModel<Option> model = new DefaultComboBoxModel<>();
        if (first != null) {
            model.addElement(first);
        }
        Option selected = first;
        for (MasterItem item : items) {
            boolean isSelected = item.id().equals(selectedId);
            if (item.active() || isSelected) {
                Option option = new Option(item.id(), Names.label(item));
                model.addElement(option);
                if (isSelected) {
                    selected = option;
                }
            }
        }
        combo.setModel(model);
        if (selected != null) {
            combo.setSelectedItem(selected);
        } else if (model.getSize() > 0) {
            combo.setSelectedIndex(0);
        }
    }

    private void selectTags(Set<String> ids) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < tagModel.size(); i++) {
            if (ids.contains(tagModel.get(i).id())) {
                indices.add(i);
            }
        }
        tags.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
    }

    void setSpec(TaskSpec spec) {
        fill(project, NO_PROJECT, masters.projects(), spec.projectId());
        fill(category, null, masters.categories(), spec.categoryId());
        taskName.setText(Objects.toString(spec.taskName(), ""));
        description.setText(Objects.toString(spec.description(), ""));
        selectTags(spec.tagIds());
    }

    TaskSpec getSpec() {
        Option p = (Option) project.getSelectedItem();
        Option c = (Option) category.getSelectedItem();
        Set<String> tagIds = new LinkedHashSet<>();
        for (Option o : tags.getSelectedValuesList()) {
            tagIds.add(o.id());
        }
        return new TaskSpec(p == null ? null : p.id(), c == null ? null : c.id(), taskName.getText(),
                description.getText(), tagIds);
    }

    /** 作業名・作業内容・タグを空にする（プロジェクトとカテゴリは維持）。 */
    void clearTask() {
        taskName.setText("");
        description.setText("");
        tags.clearSelection();
    }

    void focusTaskName() {
        taskName.requestFocusInWindow();
    }

    private static final class TagRenderer extends JCheckBox implements ListCellRenderer<Option> {
        TagRenderer() {
            Ui.noHtml(this);
            Theme.styleToggle(this);
            setOpaque(true); // リストの行として背景を塗る
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Option> list, Option value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            setText(value.label());
            setSelected(isSelected);
            setBackground(list.getBackground());
            setForeground(list.getForeground());
            setFont(list.getFont());
            return this;
        }
    }
}
