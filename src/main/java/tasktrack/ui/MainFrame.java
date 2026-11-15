package tasktrack.ui;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;

import tasktrack.AppContext;

/** メイン画面。左端にサイドメニュー、その隣にタイマー（常時表示）、右に選択中の機能の画面を配置する。 */
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

        Theme.Nav menu = Theme.Nav.sideMenu("TaskTrack")
                .add("作業記録", new EntriesPanel(ctx, events))
                .add("集計", new SummaryPanel(ctx, events))
                .add("日次振り返り", reviewPanel)
                .add("マスタ管理", new MasterPanel(ctx, events))
                .add("データ出力", new ExportPanel(ctx))
                .add("バックアップ", new BackupPanel(ctx, this::afterRestore));

        // グラデーション背景の上に、タイマーと選択中の画面を透過で載せる
        JPanel main = Theme.gradientPanel(new BorderLayout());
        main.add(new TimerPanel(ctx, events), BorderLayout.WEST);
        main.add(menu.content(), BorderLayout.CENTER);
        Theme.clear(main);

        setLayout(new BorderLayout());
        add(menu.bar(), BorderLayout.WEST);
        add(main, BorderLayout.CENTER);
        setIconImages(Theme.appIcons());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        setSize(1320, 760);
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
