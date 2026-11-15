package tasktrack.app;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import tasktrack.db.MasterRepository;
import tasktrack.db.TimeEntryRepository;
import tasktrack.domain.Category;
import tasktrack.domain.MasterItem;
import tasktrack.domain.Project;
import tasktrack.domain.TimeEntry;

/**
 * 作業時間の集計。
 * <ul>
 *   <li>日付の境界は日本時間の 00:00。日付をまたぐ記録は日ごとに分割して集計する。</li>
 *   <li>週は月曜始まり。</li>
 *   <li>期間の境界をまたぐ記録は期間内の部分だけを集計する。</li>
 *   <li>計測中（未終了）の作業は集計に含めない。</li>
 * </ul>
 */
public final class SummaryService {
    public static final DayOfWeek WEEK_START = DayOfWeek.MONDAY;
    static final String NO_PROJECT = "（プロジェクトなし）";

    private final TimeEntryRepository entries;
    private final MasterRepository<Project> projects;
    private final MasterRepository<Category> categories;

    public SummaryService(TimeEntryRepository entries, MasterRepository<Project> projects,
                          MasterRepository<Category> categories) {
        this.entries = entries;
        this.projects = projects;
        this.categories = categories;
    }

    /** from〜to（両端の日を含む）を集計する。 */
    public Summary summarize(LocalDate from, LocalDate to) {
        Period.check(from, to);
        Instant rangeStart = AppTime.startOfDay(from);
        Instant rangeEnd = AppTime.startOfDay(to.plusDays(1));

        Map<String, String> projectNames = names(projects.findAll());
        Map<String, String> categoryNames = names(categories.findAll());

        Map<String, Bucket> daily = new LinkedHashMap<>();
        Map<String, Bucket> weekly = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            daily.put(d.toString(), new Bucket());
            weekly.computeIfAbsent(weekLabel(d), k -> new Bucket());
        }
        Map<String, Bucket> byCategory = new LinkedHashMap<>();
        Map<String, Bucket> byProject = new LinkedHashMap<>();

        long total = 0;
        List<TimeEntry> list = entries.findFinishedOverlapping(rangeStart, rangeEnd);
        for (TimeEntry e : list) {
            Instant start = max(e.startAt(), rangeStart);
            Instant end = min(e.endAt(), rangeEnd);
            long seconds = Math.max(0, ChronoUnit.SECONDS.between(start, end));
            total += seconds;
            byCategory.computeIfAbsent(categoryNames.getOrDefault(e.categoryId(), "（不明）"), k -> new Bucket())
                    .add(e.id(), seconds);
            String project = e.projectId() == null ? NO_PROJECT
                    : projectNames.getOrDefault(e.projectId(), "（不明）");
            byProject.computeIfAbsent(project, k -> new Bucket()).add(e.id(), seconds);

            // 日ごとに分割
            LocalDate day = AppTime.dateOf(start);
            do {
                Instant dayStart = AppTime.startOfDay(day);
                Instant dayEnd = AppTime.startOfDay(day.plusDays(1));
                long part = Math.max(0, ChronoUnit.SECONDS.between(max(start, dayStart), min(end, dayEnd)));
                daily.get(day.toString()).add(e.id(), part);
                weekly.get(weekLabel(day)).add(e.id(), part);
                day = day.plusDays(1);
            } while (AppTime.startOfDay(day).isBefore(end));
        }
        return new Summary(from, to, total, list.size(), rows(daily, false), rows(weekly, false),
                rows(byCategory, true), rows(byProject, true));
    }

    /** 週のラベル（その週の月曜〜日曜）。 */
    static String weekLabel(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(WEEK_START));
        return monday + " 〜 " + monday.plusDays(6);
    }

    private static Map<String, String> names(List<? extends MasterItem> items) {
        return items.stream().collect(Collectors.toMap(MasterItem::id,
                i -> i.active() ? i.name() : i.name() + "（無効）"));
    }

    private static List<Summary.Row> rows(Map<String, Bucket> buckets, boolean sortBySeconds) {
        List<Summary.Row> rows = buckets.entrySet().stream()
                .map(en -> new Summary.Row(en.getKey(), en.getValue().seconds, en.getValue().entryIds.size()))
                .collect(Collectors.toList());
        if (sortBySeconds) {
            rows.sort(Comparator.comparingLong(Summary.Row::seconds).reversed().thenComparing(Summary.Row::label));
        }
        return List.copyOf(rows);
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static final class Bucket {
        long seconds;
        final Set<String> entryIds = new HashSet<>();

        void add(String entryId, long s) {
            seconds += s;
            entryIds.add(entryId);
        }
    }
}
