package tasktrack.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tasktrack.domain.TimeEntry;

class TimeEntryServiceTest {
    @TempDir
    Path dir;
    TestEnv env;
    TimeEntryService service;

    @BeforeEach
    void setUp() {
        env = new TestEnv(dir);
        service = env.ctx.entries;
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    void manualEntryIsStoredAndMarkedManual() {
        TimeEntry e = service.createManual(env.manual("会議", "2026-11-08T13:00:00", "2026-11-08T14:30:00"));
        TimeEntry saved = service.find(e.id()).orElseThrow();
        assertTrue(saved.manual());
        assertEquals(5400, saved.duration(env.clock.instant()).toSeconds());
        assertEquals(env.clock.instant(), saved.createdAt());

        TimeEntry timed = env.ctx.timer.start(env.spec("実装"));
        assertFalse(service.find(timed.id()).orElseThrow().manual());
    }

    @Test
    void manualEntryRequiresEndAfterStart() {
        assertThrows(ValidationException.class,
                () -> service.createManual(env.manual("会議", "2026-11-08T13:00:00", "2026-11-08T13:00:00")));
        assertThrows(ValidationException.class,
                () -> service.createManual(env.manual("会議", "2026-11-08T13:00:00", "2026-11-08T12:00:00")));
        assertThrows(ValidationException.class,
                () -> service.createManual(new EntryInput(env.spec("会議"), null, null)));
    }

    @Test
    void updateChangesFieldsAndTagsButKeepsRegistrationMethod() {
        String tag1 = env.ctx.masters.createTag("調査").id();
        String tag2 = env.ctx.masters.createTag("手戻り").id();
        TimeEntry e = service.createManual(new EntryInput(
                new TaskSpec(null, env.categoryId, "会議", null, Set.of(tag1)),
                TestEnv.jst("2026-11-08T13:00:00"), TestEnv.jst("2026-11-08T14:00:00")));

        env.clock.advance(Duration.ofMinutes(5));
        String otherCategory = env.ctx.masters.categories().get(1).id();
        service.update(e.id(), new EntryInput(
                new TaskSpec(null, otherCategory, "定例会議", "議事録あり", Set.of(tag1, tag2)),
                TestEnv.jst("2026-11-08T13:00:00"), TestEnv.jst("2026-11-08T15:00:00")));

        TimeEntry saved = service.find(e.id()).orElseThrow();
        assertEquals("定例会議", saved.taskName());
        assertEquals("議事録あり", saved.description());
        assertEquals(otherCategory, saved.categoryId());
        assertEquals(Set.of(tag1, tag2), saved.tagIds());
        assertEquals(TestEnv.jst("2026-11-08T15:00:00"), saved.endAt());
        assertTrue(saved.manual());
        assertEquals(e.createdAt(), saved.createdAt());
        assertEquals(env.clock.instant(), saved.updatedAt());
    }

    @Test
    void runningEntryCanBeEditedWithoutStoppingIt() {
        TimeEntry running = env.ctx.timer.start(env.spec("実装"));
        service.update(running.id(), new EntryInput(env.spec("実装（修正）"), running.startAt(), null));
        TimeEntry saved = env.ctx.timer.current().orElseThrow();
        assertEquals("実装（修正）", saved.taskName());
        assertNull(saved.endAt());

        // 終了済みの記録は終了日時が必須
        env.ctx.timer.stop();
        assertThrows(ValidationException.class,
                () -> service.update(running.id(), new EntryInput(env.spec("実装"), running.startAt(), null)));
    }

    @Test
    void deleteRemovesEntryAndItsTagLinks() {
        String tag = env.ctx.masters.createTag("調査").id();
        TimeEntry e = service.createManual(new EntryInput(
                new TaskSpec(null, env.categoryId, "会議", null, Set.of(tag)),
                TestEnv.jst("2026-11-08T13:00:00"), TestEnv.jst("2026-11-08T14:00:00")));
        service.delete(e.id());
        assertTrue(service.find(e.id()).isEmpty());
        assertEquals(0, env.ctx.db.queryOne("SELECT count(*) FROM time_entry_tag", rs -> rs.getInt(1)).orElseThrow());
        // タグ自体は残る
        assertEquals(1, env.ctx.masters.tags().size());
    }

    @Test
    void listByDateReturnsEntriesOfThatDayInStartOrder() {
        service.createManual(env.manual("午後", "2026-11-08T13:00:00", "2026-11-08T14:00:00"));
        service.createManual(env.manual("午前", "2026-11-08T09:00:00", "2026-11-08T10:00:00"));
        service.createManual(env.manual("前日", "2026-11-07T09:00:00", "2026-11-07T10:00:00"));
        // 日付をまたぐ記録は両日に現れる
        TimeEntry overnight = service.createManual(env.manual("夜間", "2026-11-08T23:00:00", "2026-11-09T01:00:00"));

        LocalDate day8 = LocalDate.parse("2026-11-08");
        LocalDate day9 = LocalDate.parse("2026-11-09");
        assertEquals(List.of("午前", "午後", "夜間"),
                service.listByDate(day8).stream().map(TimeEntry::taskName).toList());
        assertEquals(List.of("夜間"), service.listByDate(day9).stream().map(TimeEntry::taskName).toList());
        assertEquals(3600, service.secondsWithin(overnight, day8));
        assertEquals(3600, service.secondsWithin(overnight, day9));
    }

    @Test
    void listByDateIncludesRunningEntry() {
        TimeEntry running = env.ctx.timer.start(env.spec("実装"));
        env.clock.advance(Duration.ofMinutes(20));
        LocalDate today = LocalDate.parse("2026-11-09");
        assertEquals(List.of(running.id()), service.listByDate(today).stream().map(TimeEntry::id).toList());
        assertEquals(1200, service.secondsWithin(running, today));
        assertTrue(service.listByDate(today.plusDays(1)).isEmpty());
    }
}
