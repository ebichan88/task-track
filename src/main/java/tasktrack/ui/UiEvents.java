package tasktrack.ui;

import java.util.ArrayList;
import java.util.List;

/** データ変更を各画面へ知らせるための簡易な通知。 */
final class UiEvents {
    private final List<Runnable> listeners = new ArrayList<>();

    void onDataChanged(Runnable listener) {
        listeners.add(listener);
    }

    void fireDataChanged() {
        List.copyOf(listeners).forEach(Runnable::run);
    }
}
