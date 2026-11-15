package tasktrack.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tasktrack.db.Database;
import tasktrack.db.TimeEntryRepository;
import tasktrack.domain.TimeEntry;

/** 作業記録の手動登録・編集・削除・履歴取得。 */
public final class TimeEntryService {
    private final Database db;
    private final TimeEntryRepository entries;
    private final TaskSpecValidator validator;
    private final Clock clock;

    public TimeEntryService(Database db, TimeEntryRepository entries, TaskSpecValidator validator, Clock clock) {
        this.db = db;
        this.entries = entries;
        this.validator = validator;
        this.clock = clock;
    }

    public Optional<TimeEntry> find(String id) {
        return entries.findById(id);
    }

    /** 開始・終了日時を指定して作業記録を手動登録する。他の記録との時間重複は許可する。 */
    public TimeEntry createManual(EntryInput input) {
        TaskSpec spec = validator.validate(input.spec());
        Instant start = requireTime(input.startAt(), "開始日時");
        Instant end = requireTime(input.endAt(), "終了日時");
        requireOrder(start, end);
        Instant now = AppTime.now(clock);
        TimeEntry entry = new TimeEntry(UUID.randomUUID().toString(), spec.projectId(), spec.categoryId(),
                spec.taskName(), spec.description(), start, end, true, spec.tagIds(), now, now);
        entries.insert(entry);
        return entry;
    }

    /**
     * 作業記録を編集する。登録方法（タイマー／手動）は変更しない。
     * 計測中の記録は終了日時を空のままにすれば計測を継続し、終了日時を入れればその時刻で終了する。
     */
    public TimeEntry update(String id, EntryInput input) {
        TaskSpec spec = validator.validate(input.spec());
        return db.transaction(() -> {
            TimeEntry existing = entries.findById(id)
                    .orElseThrow(() -> new ValidationException("編集対象の作業記録が見つかりません。"));
            Instant start = requireTime(input.startAt(), "開始日時");
            Instant end = null;
            if (input.endAt() != null || !existing.running()) {
                end = requireTime(input.endAt(), "終了日時");
                requireOrder(start, end);
            } else if (start.isAfter(clock.instant())) {
                throw new ValidationException("計測中の作業の開始日時に未来の日時は指定できません。");
            }
            TimeEntry updated = new TimeEntry(existing.id(), spec.projectId(), spec.categoryId(), spec.taskName(),
                    spec.description(), start, end, existing.manual(), spec.tagIds(), existing.createdAt(),
                    AppTime.now(clock));
            entries.update(updated);
            return updated;
        });
    }

    public void delete(String id) {
        entries.delete(id);
    }

    /**
     * 指定日（日本時間 00:00〜翌 00:00）に重なる作業記録を開始日時順で返す。
     * 日付をまたぐ記録は両方の日に現れる。計測中の作業も（その日に掛かっていれば）含める。
     */
    public List<TimeEntry> listByDate(LocalDate date) {
        Instant from = AppTime.startOfDay(date);
        Instant to = AppTime.startOfDay(date.plusDays(1));
        List<TimeEntry> result = new ArrayList<>(entries.findFinishedOverlapping(from, to));
        Instant now = clock.instant();
        for (TimeEntry running : entries.findUnfinished()) {
            if (running.startAt().isBefore(to) && (!now.isBefore(from) || !running.startAt().isBefore(from))) {
                result.add(running);
            }
        }
        result.sort(Comparator.comparing(TimeEntry::startAt));
        return result;
    }

    /** 記録のうち指定日に含まれる部分の秒数（計測中は現在時刻まで）。 */
    public long secondsWithin(TimeEntry entry, LocalDate date) {
        Instant from = AppTime.startOfDay(date);
        Instant to = AppTime.startOfDay(date.plusDays(1));
        Instant start = entry.startAt().isAfter(from) ? entry.startAt() : from;
        Instant rawEnd = entry.endAt() != null ? entry.endAt() : AppTime.now(clock);
        Instant end = rawEnd.isBefore(to) ? rawEnd : to;
        return end.isAfter(start) ? ChronoUnit.SECONDS.between(start, end) : 0;
    }

    private static Instant requireTime(Instant t, String label) {
        if (t == null) {
            throw new ValidationException(label + "を入力してください。");
        }
        return t.truncatedTo(ChronoUnit.SECONDS);
    }

    private static void requireOrder(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new ValidationException("終了日時は開始日時より後を指定してください。");
        }
    }
}
