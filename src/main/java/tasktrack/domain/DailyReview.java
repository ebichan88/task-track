package tasktrack.domain;

import java.time.Instant;
import java.time.LocalDate;

/** 日次振り返り（1日につき1件）。 */
public record DailyReview(
        String id,
        LocalDate date,
        String achievements,
        String blockers,
        String decisions,
        String nextActions,
        String reflections,
        Instant createdAt,
        Instant updatedAt) {
}
