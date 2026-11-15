package tasktrack.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tasktrack.domain.Category;

class SummaryServiceTest {
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

    private void entry(String projectId, String categoryId, String start, String end) {
        env.ctx.entries.createManual(new EntryInput(new TaskSpec(projectId, categoryId, "作業", null, Set.of()),
                TestEnv.jst(start), TestEnv.jst(end)));
    }

    @Test
    void summarizesByDayWeekCategoryAndProject() {
        List<Category> cats = env.ctx.masters.categories();
        Category design = cats.stream().filter(c -> c.name().equals("設計・技術調査")).findFirst().orElseThrow();
        Category impl = cats.stream().filter(c -> c.name().equals("実装・コーディング")).findFirst().orElseThrow();
        String projectA = env.ctx.masters.createProject("案件A", "").id();

        // 2026-11-09 は月曜日
        entry(projectA, design.id(), "2026-11-09T09:00:00", "2026-11-09T11:00:00"); // 月 2h
        entry(projectA, impl.id(), "2026-11-09T13:00:00", "2026-11-09T14:00:00");   // 月 1h
        entry(null, impl.id(), "2026-11-15T22:00:00", "2026-11-16T01:00:00");       // 日 2h + 翌週の月 1h
        entry(null, impl.id(), "2026-11-20T09:00:00", "2026-11-20T09:30:00");       // 期間外

        Summary s = env.ctx.summary.summarize(LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-16"));

        assertEquals(6 * 3600, s.totalSeconds());
        assertEquals(3, s.entryCount());

        assertEquals(8, s.daily().size());
        assertEquals(new Summary.Row("2026-11-09", 3 * 3600, 2), s.daily().get(0));
        assertEquals(new Summary.Row("2026-11-10", 0, 0), s.daily().get(1));
        assertEquals(new Summary.Row("2026-11-15", 2 * 3600, 1), s.daily().get(6));
        assertEquals(new Summary.Row("2026-11-16", 3600, 1), s.daily().get(7));

        assertEquals(List.of(
                new Summary.Row("2026-11-09 〜 2026-11-15", 5 * 3600, 3),
                new Summary.Row("2026-11-16 〜 2026-11-22", 3600, 1)), s.weekly());

        assertEquals(List.of(
                new Summary.Row("実装・コーディング", 4 * 3600, 2),
                new Summary.Row("設計・技術調査", 2 * 3600, 1)), s.byCategory());

        assertEquals(List.of(
                new Summary.Row("案件A", 3 * 3600, 2),
                new Summary.Row(SummaryService.NO_PROJECT, 3 * 3600, 1)), s.byProject());
    }

    @Test
    void clipsEntriesAtPeriodBoundaryAndIgnoresRunningTimer() {
        entry(null, env.categoryId, "2026-11-08T23:00:00", "2026-11-09T01:00:00");
        env.ctx.timer.start(env.spec("計測中"));

        Summary s = env.ctx.summary.summarize(LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-09"));
        assertEquals(3600, s.totalSeconds());
        assertEquals(1, s.entryCount());
        assertEquals(List.of(new Summary.Row("2026-11-09", 3600, 1)), s.daily());
    }

    @Test
    void rejectsInvalidPeriod() {
        assertThrows(ValidationException.class,
                () -> env.ctx.summary.summarize(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-09")));
        assertThrows(ValidationException.class, () -> env.ctx.summary.summarize(null, null));
    }
}
