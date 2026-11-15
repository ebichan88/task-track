package tasktrack.app;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tasktrack.db.Database;
import tasktrack.db.TimeEntryRepository;
import tasktrack.domain.TimeEntry;

/**
 * タイマー制御。計測中の作業は「終了日時が未設定の作業記録」として DB に永続化する。
 * 同時に計測できる作業は1つのみ。
 */
public final class TimerService {
    private final Database db;
    private final TimeEntryRepository entries;
    private final TaskSpecValidator validator;
    private final Clock clock;

    public TimerService(Database db, TimeEntryRepository entries, TaskSpecValidator validator, Clock clock) {
        this.db = db;
        this.entries = entries;
        this.validator = validator;
        this.clock = clock;
    }

    /** 計測中の作業。 */
    public Optional<TimeEntry> current() {
        return entries.findUnfinished().stream().findFirst();
    }

    /**
     * 作業を開始する。計測中の作業があれば同一時刻で終了させてから開始する（作業の切り替え）。
     * 前の作業の終了と新しい作業の作成は1トランザクションで行う。
     */
    public TimeEntry start(TaskSpec spec) {
        TaskSpec valid = validator.validate(spec);
        return db.transaction(() -> {
            Instant at = AppTime.now(clock);
            for (TimeEntry running : entries.findUnfinished()) {
                // 時計が巻き戻っていても終了 >= 開始 を保ち、前の終了と次の開始を一致させる
                if (at.isBefore(running.startAt())) {
                    at = running.startAt();
                }
                entries.finish(running.id(), at, at);
            }
            TimeEntry entry = new TimeEntry(UUID.randomUUID().toString(), valid.projectId(), valid.categoryId(),
                    valid.taskName(), valid.description(), at, null, false, valid.tagIds(), at, at);
            entries.insert(entry);
            return entry;
        });
    }

    /** 計測中の作業を現在時刻で停止する。計測中でなければ何もしない。 */
    public Optional<TimeEntry> stop() {
        return db.transaction(() -> {
            Optional<TimeEntry> running = current();
            running.ifPresent(e -> finish(e, AppTime.now(clock)));
            return running.flatMap(e -> entries.findById(e.id()));
        });
    }

    /** 過去の作業記録と同じ内容で、新しい作業記録として計測を開始する。元の記録は変更しない。 */
    public TimeEntry resume(String entryId) {
        TimeEntry source = entries.findById(entryId)
                .orElseThrow(() -> new ValidationException("再開する作業記録が見つかりません。"));
        return start(TaskSpec.of(source));
    }

    // --- 異常終了時の補正 -------------------------------------------------------

    /** 終了日時が未設定の作業記録（前回の異常終了で残ったもの）。 */
    public List<TimeEntry> findUnfinished() {
        return entries.findUnfinished();
    }

    /** 未終了記録を現在時刻で終了する。 */
    public void finishNow(String entryId) {
        finish(requireUnfinished(entryId), AppTime.now(clock));
    }

    /** 未終了記録を指定した日時で終了する。開始日時より前・現在より後は指定できない。 */
    public void finishAt(String entryId, Instant endAt) {
        TimeEntry entry = requireUnfinished(entryId);
        if (endAt == null) {
            throw new ValidationException("終了日時を入力してください。");
        }
        if (endAt.isBefore(entry.startAt())) {
            throw new ValidationException("終了日時は開始日時以降を指定してください。");
        }
        if (endAt.isAfter(clock.instant())) {
            throw new ValidationException("終了日時に未来の日時は指定できません。");
        }
        entries.finish(entry.id(), endAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS), AppTime.now(clock));
    }

    /** 未終了記録を破棄（削除）する。 */
    public void discard(String entryId) {
        entries.delete(requireUnfinished(entryId).id());
    }

    private void finish(TimeEntry entry, Instant now) {
        Instant end = now.isBefore(entry.startAt()) ? entry.startAt() : now;
        entries.finish(entry.id(), end, now);
    }

    private TimeEntry requireUnfinished(String entryId) {
        return entries.findById(entryId)
                .filter(TimeEntry::running)
                .orElseThrow(() -> new ValidationException("未終了の作業記録が見つかりません。"));
    }
}
