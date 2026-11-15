package tasktrack.app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import tasktrack.db.DailyReviewRepository;
import tasktrack.db.MasterRepository;
import tasktrack.db.TagRepository;
import tasktrack.db.TimeEntryRepository;
import tasktrack.domain.Category;
import tasktrack.domain.DailyReview;
import tasktrack.domain.Project;
import tasktrack.domain.Tag;
import tasktrack.domain.TimeEntry;

/**
 * 指定期間のデータを CSV にし、ZIP にまとめて出力する。外部通信は一切行わない。
 * <ul>
 *   <li>期間は開始日 00:00（含む）〜終了日の翌日 00:00（含まない）。いずれも日本時間。</li>
 *   <li>期間境界をまたぐ作業記録は期間内の部分のみを出力する。</li>
 *   <li>segment_id は「entry_id + '_' + 出力区間の開始時刻(UTC, yyyyMMdd'T'HHmmss'Z')」。
 *       同じ区間は何度出力しても同じ ID になる。</li>
 *   <li>計測中（未終了）の作業記録は出力しない。</li>
 *   <li>マスタ（プロジェクト・カテゴリ・タグ）は、出力される作業記録から参照されているものだけを出力する。</li>
 * </ul>
 */
public final class ExportService {
    /** ZIP に格納するファイル名（格納順）。 */
    public static final List<String> FILE_NAMES = List.of("time_entries.csv", "daily_reviews.csv", "projects.csv",
            "categories.csv", "tags.csv", "time_entry_tags.csv");

    private static final DateTimeFormatter SEGMENT_TIME =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final TimeEntryRepository entries;
    private final DailyReviewRepository reviews;
    private final MasterRepository<Project> projects;
    private final MasterRepository<Category> categories;
    private final TagRepository tags;

    public ExportService(TimeEntryRepository entries, DailyReviewRepository reviews,
                         MasterRepository<Project> projects, MasterRepository<Category> categories,
                         TagRepository tags) {
        this.entries = entries;
        this.reviews = reviews;
        this.projects = projects;
        this.categories = categories;
        this.tags = tags;
    }

    /** 例: business-analysis-2026-11-09_2026-11-15.zip */
    public static String defaultFileName(LocalDate from, LocalDate to) {
        return "business-analysis-" + from + "_" + to + ".zip";
    }

    /** from〜to（両端の日を含む）のデータを {@code zipFile} へ出力する。既存ファイルは置き換える。 */
    public void export(LocalDate from, LocalDate to, Path zipFile) {
        Map<String, byte[]> files = buildCsvFiles(from, to);
        Path target = zipFile.toAbsolutePath();
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp);
                 ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Map.Entry<String, byte[]> f : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(f.getKey()));
                    zip.write(f.getValue());
                    zip.closeEntry();
                }
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // 後始末の失敗は元の例外を優先する
            }
            throw new ValidationException("ZIP ファイルを出力できませんでした: " + e.getMessage());
        }
    }

    /** ファイル名 → CSV バイト列（格納順）。 */
    Map<String, byte[]> buildCsvFiles(LocalDate from, LocalDate to) {
        Period.check(from, to);
        Instant rangeStart = AppTime.startOfDay(from);
        Instant rangeEnd = AppTime.startOfDay(to.plusDays(1));

        Map<String, Project> projectMap = new LinkedHashMap<>();
        projects.findAll().forEach(p -> projectMap.put(p.id(), p));
        Map<String, Category> categoryMap = new LinkedHashMap<>();
        categories.findAll().forEach(c -> categoryMap.put(c.id(), c));

        CsvBuilder entryCsv = new CsvBuilder("entry_id", "segment_id", "project_id", "project_name", "category_id",
                "category_name", "task_name", "description", "start_at", "end_at", "duration_seconds", "is_manual",
                "created_at", "updated_at");
        CsvBuilder entryTagCsv = new CsvBuilder("entry_id", "tag_id");
        Set<String> usedProjects = new TreeSet<>();
        Set<String> usedCategories = new TreeSet<>();
        Set<String> usedTags = new TreeSet<>();

        for (TimeEntry e : entries.findFinishedOverlapping(rangeStart, rangeEnd)) {
            Instant start = e.startAt().isBefore(rangeStart) ? rangeStart : e.startAt();
            Instant end = e.endAt().isAfter(rangeEnd) ? rangeEnd : e.endAt();
            Project project = e.projectId() == null ? null : projectMap.get(e.projectId());
            Category category = categoryMap.get(e.categoryId());
            entryCsv.row(e.id(), e.id() + "_" + SEGMENT_TIME.format(start),
                    e.projectId(), project == null ? null : project.name(),
                    e.categoryId(), category == null ? null : category.name(),
                    e.taskName(), e.description(),
                    AppTime.formatIso(start), AppTime.formatIso(end),
                    Math.max(0, ChronoUnit.SECONDS.between(start, end)), e.manual(),
                    AppTime.formatIso(e.createdAt()), AppTime.formatIso(e.updatedAt()));
            if (e.projectId() != null) {
                usedProjects.add(e.projectId());
            }
            usedCategories.add(e.categoryId());
            for (String tagId : new TreeSet<>(e.tagIds())) {
                entryTagCsv.row(e.id(), tagId);
                usedTags.add(tagId);
            }
        }

        CsvBuilder reviewCsv = new CsvBuilder("review_id", "review_date", "achievements", "blockers", "decisions",
                "next_actions", "reflections", "created_at", "updated_at");
        for (DailyReview r : reviews.findBetween(from, to)) {
            reviewCsv.row(r.id(), r.date().toString(), r.achievements(), r.blockers(), r.decisions(),
                    r.nextActions(), r.reflections(), AppTime.formatIso(r.createdAt()),
                    AppTime.formatIso(r.updatedAt()));
        }

        CsvBuilder projectCsv = new CsvBuilder("project_id", "project_name", "description", "is_active");
        for (Project p : projectMap.values()) {
            if (usedProjects.contains(p.id())) {
                projectCsv.row(p.id(), p.name(), p.description(), p.active());
            }
        }
        CsvBuilder categoryCsv = new CsvBuilder("category_id", "category_name", "description", "is_active");
        for (Category c : categoryMap.values()) {
            if (usedCategories.contains(c.id())) {
                categoryCsv.row(c.id(), c.name(), c.description(), c.active());
            }
        }
        CsvBuilder tagCsv = new CsvBuilder("tag_id", "tag_name");
        for (Tag t : tags.findAll()) {
            if (usedTags.contains(t.id())) {
                tagCsv.row(t.id(), t.name());
            }
        }

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("time_entries.csv", entryCsv.toBytes());
        files.put("daily_reviews.csv", reviewCsv.toBytes());
        files.put("projects.csv", projectCsv.toBytes());
        files.put("categories.csv", categoryCsv.toBytes());
        files.put("tags.csv", tagCsv.toBytes());
        files.put("time_entry_tags.csv", entryTagCsv.toBytes());
        return files;
    }
}
