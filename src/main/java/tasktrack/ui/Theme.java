package tasktrack.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * 画面の配色と、テーマに沿った部品（グラデーション背景・ボタン・見出し・メニュー）。
 * 配色は StepCounter の GUI に合わせている。ボタン類は自前で描画するため、OS の Look&amp;Feel に左右されない。
 */
final class Theme {
    /** 背景グラデーション（上端） */
    static final Color BACKGROUND_TOP = new Color(255, 240, 245);
    /** 背景グラデーション（下端） */
    static final Color BACKGROUND_BOTTOM = new Color(255, 192, 203);
    /** サイドメニュー・強調の色 */
    static final Color ACCENT = new Color(240, 128, 128);
    /** 押下・選択中の濃い強調色 */
    static final Color ACCENT_DARK = new Color(205, 92, 92);
    /** 見出しの飾り線の色 */
    static final Color ACCENT_LINE = new Color(250, 128, 114);
    /** 強調色の上に載せる文字色 */
    static final Color ON_ACCENT = new Color(248, 248, 255);
    /** 表の見出し・枠などに使う淡い色 */
    static final Color SOFT = new Color(255, 228, 225);
    static final Color SOFT_LINE = new Color(240, 200, 205);
    /** 入力欄の枠線 */
    static final Color FIELD_LINE = new Color(226, 170, 176);
    static final Color TABLE_SELECTION = new Color(255, 214, 220);
    /** 淡い背景の上の補足文字 */
    static final Color MUTED_TEXT = new Color(130, 110, 114);

    private static final Color PRIMARY = new Color(232, 106, 110);
    private static final int ARC = 12;

    private Theme() {
    }

    // --- ボタン ------------------------------------------------------------------

    /** 画面の主操作（開始・保存など）のボタン。塗りつぶしで目立たせる。 */
    static JButton primaryButton(String text) {
        return new StyledButton(text, true);
    }

    /** 主操作を大きく表示するボタン。 */
    static JButton heroButton(String text) {
        JButton button = new StyledButton(text, true);
        button.setFont(button.getFont().deriveFont(Font.BOLD, button.getFont().getSize2D() + 3f));
        button.setBorder(BorderFactory.createEmptyBorder(11, 18, 11, 18));
        return button;
    }

    /** 通常のボタン。白地に強調色の枠。 */
    static JButton button(String text) {
        return new StyledButton(text, false);
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (hints instanceof Map<?, ?> map) {
            g2.addRenderingHints(map);
        } else {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
        return g2;
    }

    private static void drawText(Graphics2D g2, AbstractButton b, Color color, int x, boolean centered, int shift) {
        FontMetrics fm = g2.getFontMetrics(b.getFont());
        String text = b.getText() == null ? "" : b.getText();
        int tx = centered ? (b.getWidth() - fm.stringWidth(text)) / 2 : x;
        int ty = (b.getHeight() - fm.getHeight()) / 2 + fm.getAscent() + shift;
        g2.setFont(b.getFont());
        g2.setColor(color);
        g2.drawString(text, tx, ty);
    }

    private static void plain(AbstractButton b) {
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** 角丸のボタン。背景・文字とも自前で描画する。 */
    private static final class StyledButton extends JButton {
        private final boolean primary;

        StyledButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            plain(this);
            setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
            if (primary) {
                setFont(getFont().deriveFont(Font.BOLD));
            }
        }

        @Override
        public void updateUI() {
            setUI(new BasicButtonUI());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth();
            int h = getHeight();
            boolean pressed = getModel().isArmed() && getModel().isPressed();
            boolean hover = getModel().isRollover();
            Color fill;
            Color line;
            Color text;
            if (!isEnabled()) {
                fill = primary ? new Color(240, 204, 206) : new Color(252, 246, 247);
                line = primary ? fill : new Color(232, 212, 215);
                text = primary ? Color.WHITE : new Color(196, 176, 180);
            } else if (primary) {
                fill = pressed ? ACCENT_DARK : hover ? ACCENT : PRIMARY;
                line = fill;
                text = ON_ACCENT;
            } else {
                fill = pressed ? new Color(255, 212, 214) : hover ? new Color(255, 236, 236) : Color.WHITE;
                line = ACCENT;
                text = ACCENT_DARK;
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
            g2.setColor(line);
            g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
            if (isFocusOwner() && isEnabled()) {
                g2.setColor(primary ? new Color(255, 255, 255, 190) : ACCENT);
                g2.drawRoundRect(2, 2, w - 5, h - 5, ARC - 4, ARC - 4);
            }
            drawText(g2, this, text, 0, true, pressed ? 1 : 0);
            g2.dispose();
        }
    }

    // --- 背景・枠 ----------------------------------------------------------------

    /** 縦方向のグラデーションを背景に持つパネル。上に載せる部品は {@link #clear} で透過にする。 */
    static JPanel gradientPanel(LayoutManager layout) {
        return new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0f, 0f, BACKGROUND_TOP, 0f, getHeight(), BACKGROUND_BOTTOM));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
    }

    /** 半透明の白い角丸カード。 */
    static JPanel card(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                g2.setColor(new Color(255, 255, 255, 185));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.setColor(new Color(255, 255, 255, 235));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        return panel;
    }

    /** 見出し（左に太い帯、下に線）付きの枠で部品を囲む。 */
    static JPanel section(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new SectionBorder(title));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private static final class SectionBorder extends AbstractBorder {
        private static final int BAR = 5;
        private final String title;

        SectionBorder(String title) {
            this.title = title;
        }

        private static Font font(Component c) {
            Font base = c.getFont() != null ? c.getFont() : UIManager.getFont("Label.font");
            return base.deriveFont(Font.BOLD);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(c.getFontMetrics(font(c)).getHeight() + 12, 0, 0, 0);
            return insets;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = smooth(g);
            Font font = font(c);
            FontMetrics fm = c.getFontMetrics(font);
            int textHeight = fm.getHeight();
            g2.setColor(ACCENT_LINE);
            g2.fillRect(x, y + 1, BAR, textHeight + 3);
            g2.fillRect(x, y + textHeight + 4, width, 2);
            g2.setFont(font);
            g2.setColor(new Color(60, 44, 48));
            g2.drawString(title, x + BAR + 7, y + 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /**
     * グラデーション背景が見えるよう配下のパネルを透過にし、入力部品（チェックボックス・選択欄・日付欄など）と表の枠をテーマの見た目にする。
     * スクロール領域の中（表・リスト・テキスト）は白地のまま残す。
     */
    static <T extends Container> T clear(T root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JScrollPane scroll) {
                scroll.setBorder(BorderFactory.createLineBorder(SOFT_LINE));
                scroll.getViewport().setBackground(Color.WHITE);
            } else if (c instanceof JComboBox<?> combo) {
                styleCombo(combo);
            } else if (c instanceof JSpinner spinner) {
                styleSpinner(spinner);
            } else if (c instanceof JTextField field) {
                styleField(field);
            } else if (c instanceof JCheckBox || c instanceof JRadioButton) {
                styleToggle((JToggleButton) c);
            } else if (c instanceof JPanel panel) {
                panel.setOpaque(false);
                clear(panel);
            }
        }
        return root;
    }

    // --- 入力部品 ----------------------------------------------------------------

    /** チェックボックス・ラジオボタンの印をテーマのものに差し替え、背景を透過にする。 */
    static void styleToggle(JToggleButton b) {
        Icon icon = b instanceof JRadioButton ? new MarkIcon(true) : new MarkIcon(false);
        b.setIcon(icon);
        b.setSelectedIcon(icon);
        b.setPressedIcon(icon);
        b.setRolloverIcon(icon);
        b.setRolloverSelectedIcon(icon);
        b.setDisabledIcon(icon);
        b.setDisabledSelectedIcon(icon);
        b.setRolloverEnabled(true);
        b.setIconTextGap(6);
        b.setOpaque(false);
    }

    private static void styleField(JTextField field) {
        field.setBorder(new FieldBorder(4, 6, 4, 6));
        repaintOnFocus(field);
    }

    private static void styleCombo(JComboBox<?> combo) {
        combo.setUI(new ComboUi());
        combo.setBackground(Color.WHITE);
        combo.setOpaque(true);
        combo.setBorder(new FieldBorder(2, 3, 2, 2));
    }

    private static void styleSpinner(JSpinner spinner) {
        spinner.setUI(new SpinnerUi());
        spinner.setBackground(Color.WHITE);
        spinner.setOpaque(true);
        spinner.setBorder(new FieldBorder(1, 1, 1, 1));
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            JTextField text = editor.getTextField();
            text.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
            text.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    spinner.repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    spinner.repaint();
                }
            });
        }
    }

    private static void repaintOnFocus(JComponent c) {
        c.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                c.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                c.repaint();
            }
        });
    }

    /** チェックボックス（角丸の四角にチェック）とラジオボタン（丸に点）の印。状態はボタンから読み取って描き分ける。 */
    private static final class MarkIcon implements Icon {
        private static final int SIZE = 16;
        private final boolean round;

        MarkIcon(boolean round) {
            this.round = round;
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            ButtonModel model = ((AbstractButton) c).getModel();
            boolean enabled = model.isEnabled();
            boolean selected = model.isSelected();
            Graphics2D g2 = smooth(g);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            int arc = round ? SIZE : 6;
            Color accent = enabled ? PRIMARY : new Color(240, 204, 206);
            boolean filled = selected && !round;
            g2.setColor(filled ? accent : enabled ? Color.WHITE : new Color(252, 246, 247));
            g2.fillRoundRect(0, 0, SIZE - 1, SIZE - 1, arc, arc);
            g2.setColor(selected ? accent : !enabled ? new Color(232, 212, 215)
                    : model.isRollover() ? ACCENT : FIELD_LINE);
            g2.setStroke(new BasicStroke(model.isPressed() && model.isArmed() ? 2f : 1.2f));
            g2.drawRoundRect(0, 0, SIZE - 1, SIZE - 1, arc, arc);
            if (selected && round) {
                g2.setColor(accent);
                g2.fillOval(4, 4, SIZE - 8, SIZE - 8);
            } else if (selected) {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D check = new Path2D.Float();
                check.moveTo(3.8, 8.0);
                check.lineTo(6.6, 10.8);
                check.lineTo(11.4, 4.8);
                g2.draw(check);
            }
            g2.dispose();
        }
    }

    /** 入力欄の枠線。フォーカスがある間は強調色にする。 */
    private static final class FieldBorder extends AbstractBorder {
        private final Insets padding;

        FieldBorder(int top, int left, int bottom, int right) {
            this.padding = new Insets(top + 1, left + 1, bottom + 1, right + 1);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(padding.top, padding.left, padding.bottom, padding.right);
            return insets;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            boolean focused = focus != null && c.isEnabled() && (focus == c || SwingUtilities.isDescendingFrom(focus, c));
            g.setColor(focused ? ACCENT : c.isEnabled() ? FIELD_LINE : new Color(232, 212, 215));
            g.drawRect(x, y, width - 1, height - 1);
        }
    }

    /** 選択欄・日付欄の矢印ボタン。 */
    private static final class ArrowButton extends JButton {
        private final boolean up;

        ArrowButton(boolean up) {
            this.up = up;
            plain(this);
            setFocusable(false);
            setBorder(BorderFactory.createEmptyBorder());
            setPreferredSize(new Dimension(20, 10));
        }

        @Override
        public void updateUI() {
            setUI(new BasicButtonUI());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            boolean pressed = getModel().isArmed() && getModel().isPressed();
            if (isEnabled() && (pressed || getModel().isRollover())) {
                // 外周1pxは塗らない（日付欄では矢印が枠線の上に重なるため）
                g2.setColor(pressed ? TABLE_SELECTION : SOFT);
                g2.fillRect(1, 1, getWidth() - 2, getHeight() - 2);
            }
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float half = Math.min(4f, getHeight() / 2f - 2.5f);
            float rise = (up ? -1 : 1) * half * 0.55f;
            Path2D arrow = new Path2D.Float();
            arrow.moveTo(cx - half, cy - rise);
            arrow.lineTo(cx, cy + rise);
            arrow.lineTo(cx + half, cy - rise);
            g2.setColor(isEnabled() ? ACCENT_DARK : new Color(220, 196, 200));
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(arrow);
            g2.dispose();
        }
    }

    private static final class ComboUi extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            return new ArrowButton(false);
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox);
            popup.setBorder(BorderFactory.createLineBorder(ACCENT));
            popup.getList().setSelectionBackground(TABLE_SELECTION);
            popup.getList().setSelectionForeground(Color.BLACK);
            popup.getList().setBackground(Color.WHITE);
            return popup;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            g.setColor(comboBox.isEnabled() ? Color.WHITE : new Color(252, 246, 247));
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        /** 選択中の値。フォーカス時も反転表示にせず、枠線の色だけで示す。 */
        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            Component c = comboBox.getRenderer().getListCellRendererComponent(listBox, comboBox.getSelectedItem(),
                    -1, false, false);
            c.setFont(comboBox.getFont());
            c.setBackground(comboBox.isEnabled() ? Color.WHITE : new Color(252, 246, 247));
            c.setForeground(comboBox.isEnabled() ? comboBox.getForeground() : MUTED_TEXT);
            currentValuePane.paintComponent(g, c, comboBox, bounds.x + 2, bounds.y, bounds.width - 2, bounds.height,
                    c instanceof JPanel);
        }
    }

    private static final class SpinnerUi extends BasicSpinnerUI {
        @Override
        protected Component createNextButton() {
            JButton button = new ArrowButton(true);
            installNextButtonListeners(button);
            return button;
        }

        @Override
        protected Component createPreviousButton() {
            JButton button = new ArrowButton(false);
            installPreviousButtonListeners(button);
            return button;
        }
    }

    // --- 表 ----------------------------------------------------------------------

    static void styleTable(JTable table) {
        table.setBackground(Color.WHITE);
        table.setSelectionBackground(TABLE_SELECTION);
        table.setSelectionForeground(Color.BLACK);
        table.setGridColor(new Color(248, 226, 230));
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setFillsViewportHeight(true);
        DefaultTableCellRenderer header = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(t, value, false, false, row, column);
                setBackground(SOFT);
                setForeground(new Color(96, 56, 60));
                setFont(t.getFont().deriveFont(Font.BOLD));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT),
                        BorderFactory.createEmptyBorder(5, 6, 4, 6)));
                return this;
            }
        };
        table.getTableHeader().setDefaultRenderer(Ui.noHtml(header));
    }

    // --- メニュー（画面切り替え） -------------------------------------------------

    /** ボタンで画面を切り替える部品。縦型はサイドメニュー、横型はタブの代わりに使う。 */
    static final class Nav {
        private final boolean side;
        private final JPanel items;
        private final JPanel bar;
        private final CardLayout cards = new CardLayout();
        private final JPanel content = new JPanel(cards);
        private final ButtonGroup group = new ButtonGroup();
        private final List<JToggleButton> buttons = new ArrayList<>();

        private Nav(boolean side, String heading) {
            this.side = side;
            content.setOpaque(false);
            if (side) {
                items = new JPanel(new GridLayout(0, 1, 0, 2));
                items.setOpaque(false);
                bar = new JPanel(new BorderLayout(0, 14)) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        g.setColor(ACCENT);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
                bar.setBorder(BorderFactory.createEmptyBorder(18, 0, 12, 0));
                JLabel label = new JLabel(heading);
                label.setForeground(Color.WHITE);
                label.setFont(label.getFont().deriveFont(Font.BOLD, 20f));
                label.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
                JPanel top = new JPanel(new BorderLayout(0, 14));
                top.setOpaque(false);
                top.add(label, BorderLayout.NORTH);
                top.add(items, BorderLayout.CENTER);
                bar.add(top, BorderLayout.NORTH);
            } else {
                items = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
                items.setOpaque(false);
                bar = items;
            }
        }

        /** サイドメニュー型。{@link #bar()} と {@link #content()} を呼び出し側で配置する。 */
        static Nav sideMenu(String heading) {
            return new Nav(true, heading);
        }

        /** タブ型。{@link #panel()} で、切り替えボタンと内容をまとめたパネルを得る。 */
        static Nav tabs() {
            return new Nav(false, null);
        }

        Nav add(String title, JComponent page) {
            JToggleButton button = side ? new SideItem(title) : new TabItem(title);
            button.addActionListener(e -> cards.show(content, title));
            group.add(button);
            buttons.add(button);
            items.add(button);
            content.add(page, title);
            if (buttons.size() == 1) {
                button.setSelected(true);
            }
            return this;
        }

        JPanel bar() {
            return bar;
        }

        JPanel content() {
            return content;
        }

        JPanel panel() {
            JPanel panel = new JPanel(new BorderLayout(0, Ui.GAP));
            panel.setOpaque(false);
            panel.add(bar, BorderLayout.NORTH);
            panel.add(content, BorderLayout.CENTER);
            return panel;
        }
    }

    /** サイドメニューの項目。 */
    private static final class SideItem extends JToggleButton {
        SideItem(String text) {
            super(text);
            plain(this);
            setForeground(Color.WHITE);
            setFont(getFont().deriveFont(Font.BOLD, 14f));
            setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 18));
        }

        @Override
        public void updateUI() {
            setUI(new BasicToggleButtonUI());
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 150), d.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            if (isSelected()) {
                g2.setColor(ACCENT_DARK);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, 5, getHeight());
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(255, 255, 255, 50));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            if (isFocusOwner() && !isSelected()) {
                g2.setColor(new Color(255, 255, 255, 170));
                g2.drawRect(8, 3, getWidth() - 13, getHeight() - 7);
            }
            drawText(g2, this, Color.WHITE, 22, false, 0);
            g2.dispose();
        }
    }

    /** タブ型メニューの項目。 */
    private static final class TabItem extends JToggleButton {
        TabItem(String text) {
            super(text);
            plain(this);
            setFont(getFont().deriveFont(Font.BOLD));
            setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        }

        @Override
        public void updateUI() {
            setUI(new BasicToggleButtonUI());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth();
            int h = getHeight();
            boolean selected = isSelected();
            g2.setColor(selected ? ACCENT : getModel().isRollover() ? new Color(255, 255, 255, 230)
                    : new Color(255, 255, 255, 150));
            g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
            if (isFocusOwner()) {
                g2.setColor(selected ? new Color(255, 255, 255, 190) : ACCENT);
                g2.drawRoundRect(2, 2, w - 5, h - 5, h - 4, h - 4);
            }
            drawText(g2, this, selected ? Color.WHITE : ACCENT_DARK, 0, true, 0);
            g2.dispose();
        }
    }

    // --- アイコン ----------------------------------------------------------------

    /** ウィンドウ用のアイコン（強調色の円に時計の針）。 */
    static List<Image> appIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[] {16, 32, 48, 64}) {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            float s = size / 16f;
            g2.setColor(ACCENT);
            g2.fillOval(0, 0, size - 1, size - 1);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.6f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Float(8 * s, 8 * s, 8 * s, 3.6f * s));
            g2.draw(new Line2D.Float(8 * s, 8 * s, 11 * s, 9.6f * s));
            g2.dispose();
            icons.add(image);
        }
        return icons;
    }
}
