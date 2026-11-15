package tasktrack.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;

import tasktrack.AppContext;
import tasktrack.app.MasterService;
import tasktrack.domain.MasterItem;
import tasktrack.domain.Tag;

/** マスタ管理（プロジェクト・カテゴリ・タグ）。 */
final class MasterPanel extends JPanel {

    /** 新規登録処理。 */
    @FunctionalInterface
    interface Creator {
        void create(String name, String description);
    }

    /** 更新処理。 */
    @FunctionalInterface
    interface Updater {
        void update(String id, String name, String description, boolean active);
    }

    MasterPanel(AppContext ctx, UiEvents events) {
        super(new BorderLayout());
        MasterService m = ctx.masters;
        JPanel tabs = Theme.Nav.tabs()
                .add("プロジェクト", new ItemPanel("プロジェクト", events, m::projects, m::createProject, m::updateProject))
                .add("カテゴリ", new ItemPanel("カテゴリ", events, m::categories, m::createCategory, m::updateCategory))
                .add("タグ", new TagPanel(m, events))
                .panel();
        add(Ui.padded(tabs), BorderLayout.CENTER);
    }

    /** プロジェクト／カテゴリ共通の一覧・登録・編集。 */
    private static final class ItemPanel extends JPanel {
        private final String kind;
        private final UiEvents events;
        private final Supplier<? extends List<? extends MasterItem>> loader;
        private final Creator creator;
        private final Updater updater;
        private final JTable table;
        private final JButton editButton = Theme.button("編集...");
        private final JButton toggleButton = Theme.button("有効／無効を切り替え");
        private List<? extends MasterItem> rows = List.of();

        ItemPanel(String kind, UiEvents events, Supplier<? extends List<? extends MasterItem>> loader,
                  Creator creator, Updater updater) {
            super(new BorderLayout(0, Ui.GAP));
            this.kind = kind;
            this.events = events;
            this.loader = loader;
            this.creator = creator;
            this.updater = updater;
            this.table = Ui.table(kind + "名", "説明", "状態");

            JButton addButton = Theme.primaryButton("新規登録...");
            addButton.addActionListener(e -> edit(null));
            editButton.addActionListener(e -> edit(selected()));
            toggleButton.addActionListener(e -> toggle());
            table.getSelectionModel().addListSelectionListener(e -> updateButtons());
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && selected() != null) {
                        edit(selected());
                    }
                }
            });
            table.getColumnModel().getColumn(1).setPreferredWidth(320);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            buttons.add(addButton);
            buttons.add(editButton);
            buttons.add(toggleButton);

            add(new JScrollPane(table), BorderLayout.CENTER);
            JPanel south = new JPanel(new BorderLayout(0, 4));
            south.add(buttons, BorderLayout.NORTH);
            south.add(new JLabel("※ 無効にした" + kind + "は新しい作業の選択肢に出なくなります（過去の作業記録にはそのまま表示されます）。"),
                    BorderLayout.SOUTH);
            add(south, BorderLayout.SOUTH);

            events.onDataChanged(this::reload);
            reload();
        }

        private void reload() {
            String selectedId = selected() == null ? null : selected().id();
            rows = loader.get();
            Ui.model(table).setRowCount(0);
            for (MasterItem item : rows) {
                Ui.model(table).addRow(new Object[] {item.name(), item.description(), item.active() ? "有効" : "無効"});
            }
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).id().equals(selectedId)) {
                    table.setRowSelectionInterval(i, i);
                }
            }
            updateButtons();
        }

        private MasterItem selected() {
            int row = table.getSelectedRow();
            return row >= 0 && row < rows.size() ? rows.get(row) : null;
        }

        private void updateButtons() {
            editButton.setEnabled(selected() != null);
            toggleButton.setEnabled(selected() != null);
        }

        private void edit(MasterItem item) {
            JTextField name = new JTextField(item == null ? "" : item.name(), 28);
            JTextField description = new JTextField(item == null ? "" : item.description(), 28);
            JCheckBox active = new JCheckBox("有効", item == null || item.active());
            active.setEnabled(item != null);
            JPanel form = Ui.form(kind + "名 *", name, "説明", description, "状態", active);
            String title = kind + (item == null ? "の新規登録" : "の編集");
            while (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
                boolean ok = Ui.run(this, () -> {
                    if (item == null) {
                        creator.create(name.getText(), description.getText());
                    } else {
                        updater.update(item.id(), name.getText(), description.getText(), active.isSelected());
                    }
                });
                if (ok) {
                    events.fireDataChanged();
                    return;
                }
            }
        }

        private void toggle() {
            MasterItem item = selected();
            if (item != null && Ui.run(this,
                    () -> updater.update(item.id(), item.name(), item.description(), !item.active()))) {
                events.fireDataChanged();
            }
        }
    }

    /** タグの一覧・登録・名前変更・削除。 */
    private static final class TagPanel extends JPanel {
        private final MasterService masters;
        private final UiEvents events;
        private final JTable table = Ui.table("タグ名");
        private final JButton renameButton = Theme.button("名前を変更...");
        private final JButton deleteButton = Theme.button("削除");
        private List<Tag> rows = List.of();

        TagPanel(MasterService masters, UiEvents events) {
            super(new BorderLayout(0, Ui.GAP));
            this.masters = masters;
            this.events = events;

            JButton addButton = Theme.primaryButton("新規登録...");
            addButton.addActionListener(e -> add());
            renameButton.addActionListener(e -> rename());
            deleteButton.addActionListener(e -> delete());
            table.getSelectionModel().addListSelectionListener(e -> updateButtons());

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            buttons.add(addButton);
            buttons.add(renameButton);
            buttons.add(deleteButton);
            add(new JScrollPane(table), BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);

            events.onDataChanged(this::reload);
            reload();
        }

        private void reload() {
            rows = masters.tags();
            Ui.model(table).setRowCount(0);
            rows.forEach(t -> Ui.model(table).addRow(new Object[] {t.name()}));
            updateButtons();
        }

        private Tag selected() {
            int row = table.getSelectedRow();
            return row >= 0 && row < rows.size() ? rows.get(row) : null;
        }

        private void updateButtons() {
            renameButton.setEnabled(selected() != null);
            deleteButton.setEnabled(selected() != null);
        }

        private void add() {
            String name = JOptionPane.showInputDialog(this, "タグ名:", "タグの新規登録", JOptionPane.PLAIN_MESSAGE);
            if (name != null && Ui.run(this, () -> masters.createTag(name))) {
                events.fireDataChanged();
            }
        }

        private void rename() {
            Tag tag = selected();
            if (tag == null) {
                return;
            }
            Object name = JOptionPane.showInputDialog(this, "タグ名:", "タグ名の変更", JOptionPane.PLAIN_MESSAGE, null,
                    null, tag.name());
            if (name != null && Ui.run(this, () -> masters.renameTag(tag.id(), name.toString()))) {
                events.fireDataChanged();
            }
        }

        private void delete() {
            Tag tag = selected();
            if (tag != null && Ui.confirm(this, "タグ「" + tag.name() + "」を削除します。\n"
                    + "このタグを付けた作業記録からもタグが外れます。よろしいですか？", "削除の確認")
                    && Ui.run(this, () -> masters.deleteTag(tag.id()))) {
                events.fireDataChanged();
            }
        }
    }
}
