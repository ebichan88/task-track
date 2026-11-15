package tasktrack.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 作業記録。
 *
 * @param projectId 任意（未指定は null）
 * @param endAt     計測中は null
 * @param manual    true: 手動登録 / false: タイマー登録
 */
public record TimeEntry(
        String id,
        String projectId,
        String categoryId,
        String taskName,
        String description,
        Instant startAt,
        Instant endAt,
        boolean manual,
        Set<String> tagIds,
        Instant createdAt,
        Instant updatedAt) {

    public TimeEntry {
        tagIds = Set.copyOf(tagIds);
    }

    public boolean running() {
        return endAt == null;
    }

    /** 作業時間。計測中の場合は {@code now} までの経過時間（負にはならない）。 */
    public Duration duration(Instant now) {
        Instant end = endAt != null ? endAt : now;
        return end.isBefore(startAt) ? Duration.ZERO : Duration.between(startAt, end);
    }
}
