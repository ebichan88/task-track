package tasktrack.app;

import java.time.Instant;

/**
 * 作業記録の登録・編集時の入力値。
 *
 * @param endAt 計測中の記録を編集する場合のみ null 可（計測を継続する）
 */
public record EntryInput(TaskSpec spec, Instant startAt, Instant endAt) {
}
