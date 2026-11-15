package tasktrack.ui;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

import tasktrack.AppContext;

/** メイン画面。左にタイマー（常時表示）、右に各機能のタブを配置する。 */
public final class MainFrame extends JFrame {
    private final AppContext ctx;
    private final UiEvents events = new UiEvents();
    private final ReviewPanel reviewPanel;
    private final Runnable onExit;

    /** @param onExit ウィンドウを閉じて後始末を終えたあとに呼ばれる */
    public MainFrame(AppContext ctx, Runnable onExit) {
        super("TaskTrack - 業務タイムトラッキング");
        this.ctx = ctx;
        this.onExit = onExit;
        this.reviewPanel = new ReviewPanel(ctx, events);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("作業記録", new EntriesPanel(ctx, events));
        tabs.addTab("集計", new SummaryPanel(ctx, events));
        tabs.addTab("日次振り返り", reviewPanel);
        tabs.addTab("マスタ管理", new MasterPanel(ctx, events));
        tabs.addTab("データ出力", new ExportPanel(ctx));
        tabs.addTab("バックアップ", new BackupPanel(ctx, this::afterRestore));

        setLayout(new BorderLayout());
        add(new TimerPanel(ctx, events), BorderLayout.WEST);
        add(tabs, BorderLayout.CENTER);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        setSize(1240, 760);
        setLocationRelativeTo(null);
    }

    /** 起動時: 前回の異常終了で残った未終了の作業記録への対応を確認する。 */
    public void checkUnfinishedEntries() {
        if (RecoveryDialog.resolveAll(this, ctx)) {
            events.fireDataChanged();
        }
    }

    private void afterRestore() {
        events.fireDataChanged();
        checkUnfinishedEntries();
    }

    /** 正常終了: 計測中の作業があれば停止して終了日時を保存する。 */
    private void exit() {
        reviewPanel.confirmSaveIfDirty();
        Ui.run(this, ctx.timer::stop);
        dispose();
        onExit.run();
    }
}
