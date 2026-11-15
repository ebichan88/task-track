package tasktrack.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import tasktrack.db.DailyReviewRepository;
import tasktrack.db.Database;
import tasktrack.domain.DailyReview;

/** 日次振り返りの登録・編集（1日につき1件）。 */
public final class DailyReviewService {
    private final Database db;
    private final DailyReviewRepository reviews;
    private final Clock clock;

    public DailyReviewService(Database db, DailyReviewRepository reviews, Clock clock) {
        this.db = db;
        this.reviews = reviews;
        this.clock = clock;
    }

    public Optional<DailyReview> find(LocalDate date) {
        return reviews.findByDate(date);
    }

    /**
     * 指定日の振り返りを保存する（無ければ作成、あれば更新）。
     * 全項目が空の場合は、既存の振り返りがあれば削除し、無ければ何も作成しない。
     */
    public Optional<DailyReview> save(LocalDate date, ReviewInput input) {
        return db.transaction(() -> {
            Optional<DailyReview> existing = reviews.findByDate(date);
            Instant now = AppTime.now(clock);
            if (input.isEmpty()) {
                existing.ifPresent(r -> reviews.delete(r.id()));
                return Optional.<DailyReview>empty();
            }
            DailyReview saved;
            if (existing.isPresent()) {
                DailyReview old = existing.get();
                saved = new DailyReview(old.id(), date, input.achievements(), input.blockers(), input.decisions(),
                        input.nextActions(), input.reflections(), old.createdAt(), now);
                reviews.update(saved);
            } else {
                saved = new DailyReview(UUID.randomUUID().toString(), date, input.achievements(), input.blockers(),
                        input.decisions(), input.nextActions(), input.reflections(), now, now);
                reviews.insert(saved);
            }
            return Optional.of(saved);
        });
    }
}
