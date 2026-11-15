package tasktrack.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tasktrack.domain.TimeEntry;

class ExportServiceTest {
    private static final LocalDate FROM = LocalDate.parse("2026-11-09");
    private static final LocalDate TO = LocalDate.parse("2026-11-15");

    @TempDir
    Path dir;
    TestEnv env;

    @BeforeEach
    void setUp() {
        env = new TestEnv(dir);
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    /** ZIP を展開して ファイル名 → 行（BOM 除去、CRLF 区切り）にする。 */
    private Map<String, List<String>> exportAndUnzip(LocalDate from, LocalDate to) throws IOException {
        Path zip = dir.resolve(ExportService.defaultFileName(from, to));
        env.ctx.export.export(from, to, zip);
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(text.startsWith("\uFEFF"), e.getName() + " は BOM 付き");
                assertTrue(text.endsWith("\r\n"));
                assertFalse(text.replace("\r\n", "").contains("\n"), "改行は CRLF のみ（値に改行が無い前提のテスト）");
                result.put(e.getName(), new ArrayList<>(List.of(text.substring(1).split("\r\n"))));
            }
        }
        return result;
    }

    @Test
    void defaultFileNameFollowsConvention() {
        assertEquals("business-analysis-2026-11-09_2026-11-15.zip", ExportService.defaultFileName(FROM, TO));
    }

    @Test
    void zipContainsAllCsvFilesWithHeaders() throws IOException {
        Map<String, List<String>> files = exportAndUnzip(FROM, TO);
        assertEquals(ExportService.FILE_NAMES, List.copyOf(files.keySet()));
        assertEquals("\"entry_id\",\"segment_id\",\"project_id\",\"project_name\",\"category_id\",\"category_name\","
                + "\"task_name\",\"description\",\"start_at\",\"end_at\",\"duration_seconds\",\"is_manual\","
                + "\"created_at\",\"updated_at\"", files.get("time_entries.csv").get(0));
        assertEquals("\"review_id\",\"review_date\",\"achievements\",\"blockers\",\"decisions\",\"next_actions\","
                + "\"reflections\",\"created_at\",\"updated_at\"", files.get("daily_reviews.csv").get(0));
        assertEquals("\"project_id\",\"project_name\",\"description\",\"is_active\"", files.get("projects.csv").get(0));
        assertEquals("\"category_id\",\"category_name\",\"description\",\"is_active\"",
                files.get("categories.csv").get(0));
        assertEquals("\"tag_id\",\"tag_name\"", files.get("tags.csv").get(0));
        assertEquals("\"entry_id\",\"tag_id\"", files.get("time_entry_tags.csv").get(0));
        // データが無くてもヘッダーだけは出力される
        files.values().forEach(lines -> assertEquals(1, lines.size()));
    }

    @Test
    void clipsEntryCrossingEndOfPeriodAndKeepsEntryId() throws IOException {
        // 要件定義書 3.6.5 の例
        TimeEntry e = env.ctx.entries.createManual(env.manual("夜間作業", "2026-11-15T23:00:00", "2026-11-16T01:00:00"));

        List<String> lines = exportAndUnzip(FROM, TO).get("time_entries.csv");
        assertEquals(2, lines.size());
        String row = lines.get(1);
        assertTrue(row.startsWith("\"" + e.id() + "\",\"" + e.id() + "_20261115T140000Z\","), row);
        assertTrue(row.contains("\"2026-11-15T23:00:00+09:00\",\"2026-11-16T00:00:00+09:00\",\"3600\",\"true\""), row);

        // 続きの期間を出力すると、同じ entry_id・別の segment_id で残りの部分が出る
        String next = exportAndUnzip(LocalDate.parse("2026-11-16"), LocalDate.parse("2026-11-22"))
                .get("time_entries.csv").get(1);
        assertTrue(next.startsWith("\"" + e.id() + "\",\"" + e.id() + "_20261115T150000Z\","), next);
        assertTrue(next.contains("\"2026-11-16T00:00:00+09:00\",\"2026-11-16T01:00:00+09:00\",\"3600\""), next);
    }

    @Test
    void exportsOnlyDataWithinPeriod() throws IOException {
        String usedProject = env.ctx.masters.createProject("期間内案件", "").id();
        String unusedProject = env.ctx.masters.createProject("期間外案件", "").id();
        String usedTag = env.ctx.masters.createTag("期間内タグ").id();
        String unusedTag = env.ctx.masters.createTag("期間外タグ").id();

        TimeEntry in = env.ctx.entries.createManual(new EntryInput(
                new TaskSpec(usedProject, env.categoryId, "期間内", null, Set.of(usedTag)),
                TestEnv.jst("2026-11-09T00:00:00"), TestEnv.jst("2026-11-09T01:00:00")));
        env.ctx.entries.createManual(new EntryInput(
                new TaskSpec(unusedProject, env.categoryId, "期間前", null, Set.of(unusedTag)),
                TestEnv.jst("2026-11-08T22:00:00"), TestEnv.jst("2026-11-09T00:00:00")));
        env.ctx.entries.createManual(new EntryInput(
                new TaskSpec(unusedProject, env.categoryId, "期間後", null, Set.of(unusedTag)),
                TestEnv.jst("2026-11-16T00:00:00"), TestEnv.jst("2026-11-16T01:00:00")));
        env.ctx.timer.start(env.spec("計測中の作業"));

        env.ctx.reviews.save(LocalDate.parse("2026-11-08"), new ReviewInput("前日", "", "", "", ""));
        env.ctx.reviews.save(FROM, new ReviewInput("初日", "", "", "", ""));
        env.ctx.reviews.save(TO, new ReviewInput("最終日", "", "", "", ""));
        env.ctx.reviews.save(LocalDate.parse("2026-11-16"), new ReviewInput("翌日", "", "", "", ""));

        Map<String, List<String>> files = exportAndUnzip(FROM, TO);

        List<String> entries = files.get("time_entries.csv");
        assertEquals(2, entries.size());
        assertTrue(entries.get(1).contains("\"期間内\""));
        assertTrue(entries.get(1).contains("\"期間内案件\""));

        List<String> reviews = files.get("daily_reviews.csv");
        assertEquals(3, reviews.size());
        assertTrue(reviews.get(1).contains("\"2026-11-09\",\"初日\""));
        assertTrue(reviews.get(2).contains("\"2026-11-15\",\"最終日\""));

        assertEquals(List.of("\"" + usedProject + "\",\"期間内案件\",\"\",\"true\""),
                files.get("projects.csv").subList(1, files.get("projects.csv").size()));
        assertEquals(2, files.get("categories.csv").size());
        assertEquals(List.of("\"" + usedTag + "\",\"期間内タグ\""),
                files.get("tags.csv").subList(1, files.get("tags.csv").size()));
        assertEquals(List.of("\"" + in.id() + "\",\"" + usedTag + "\""),
                files.get("time_entry_tags.csv").subList(1, files.get("time_entry_tags.csv").size()));
    }

    @Test
    void writesContentAsIsExceptFormulaNeutralizationAndLeavesDatabaseUntouched() throws IOException {
        TimeEntry e = env.ctx.entries.createManual(new EntryInput(
                new TaskSpec(null, env.categoryId, "=HYPERLINK(\"http://example.invalid\")", "株式会社サンプル 山田様 \"至急\"",
                        Set.of()),
                TestEnv.jst("2026-11-10T09:00:00"), TestEnv.jst("2026-11-10T10:00:00")));

        String row = exportAndUnzip(FROM, TO).get("time_entries.csv").get(1);
        // 数式に見える値だけ先頭に ' を付け、ダブルクォートは二重化、プロジェクト未指定(NULL)は空欄
        assertTrue(row.contains("\"'=HYPERLINK(\"\"http://example.invalid\"\")\""), row);
        assertTrue(row.contains("\"株式会社サンプル 山田様 \"\"至急\"\"\""), row);
        assertTrue(row.contains("_20261110T000000Z\",,,\""), row);

        // DB 内の元データは変更されない
        assertEquals("=HYPERLINK(\"http://example.invalid\")", env.ctx.entries.find(e.id()).orElseThrow().taskName());
    }
}
