package tasktrack.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tasktrack.AppContext;
import tasktrack.domain.TimeEntry;

class TimerServiceTest {
    @TempDir
    Path dir;
    TestEnv env;
    TimerService timer;

    @BeforeEach
    void setUp() {
        env = new TestEnv(dir);
        timer = env.ctx.timer;
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    void startAndStopRecordsDuration() {
        TimeEntry started = timer.start(env.spec("設計"));
        assertTrue(started.running());
        assertFalse(started.manual());
        assertEquals(started.id(), timer.current().orElseThrow().id());

        env.clock.advance(Duration.ofMinutes(90));
        TimeEntry stopped = timer.stop().orElseThrow();

        assertEquals(TestEnv.jst("2026-11-09T10:00:00"), stopped.startAt());
        assertEquals(TestEnv.jst("2026-11-09T11:30:00"), stopped.endAt());
        assertEquals(5400, stopped.duration(Instant.MAX).toSeconds());
        assertTrue(timer.current().isEmpty());
    }

    @Test
    void stopWithoutRunningTimerDoesNothing() {
        assertTrue(timer.stop().isEmpty());
    }

    @Test
    void switchingStopsPreviousAtSameInstantAsNewStart() {
        TimeEntry a = timer.start(env.spec("作業A"));
        env.clock.advance(Duration.ofMinutes(30));
        TimeEntry b = timer.start(env.spec("作業B"));

        TimeEntry savedA = env.ctx.entries.find(a.id()).orElseThrow();
        assertEquals(b.startAt(), savedA.endAt());
        assertNotEquals(a.id(), b.id());
        // 同時に動くタイマーは1つだけ
        assertEquals(List.of(b.id()), timer.findUnfinished().stream().map(TimeEntry::id).toList());
    }

    @Test
    void switchingKeepsTimesConsistentEvenIfClockWentBackwards() {
        TimeEntry a = timer.start(env.spec("作業A"));
        env.clock.advance(Duration.ofMinutes(-5));
        TimeEntry b = timer.start(env.spec("作業B"));

        TimeEntry savedA = env.ctx.entries.find(a.id()).orElseThrow();
        assertEquals(savedA.endAt(), b.startAt());
        assertFalse(savedA.endAt().isBefore(savedA.startAt()));
    }

    @Test
    void resumeCreatesNewEntryWithSameContent() {
        String tagId = env.ctx.masters.createTag("AI利用").id();
        String projectId = env.ctx.masters.createProject("案件X", "").id();
        TimeEntry first = timer.start(new TaskSpec(projectId, env.categoryId, "実装", "詳細メモ", Set.of(tagId)));
        env.clock.advance(Duration.ofHours(1));
        timer.stop();
        env.clock.advance(Duration.ofHours(1));

        TimeEntry resumed = timer.resume(first.id());

        assertNotEquals(first.id(), resumed.id());
        assertEquals("実装", resumed.taskName());
        assertEquals("詳細メモ", resumed.description());
        assertEquals(projectId, resumed.projectId());
        assertEquals(Set.of(tagId), env.ctx.entries.find(resumed.id()).orElseThrow().tagIds());
        assertEquals(TestEnv.jst("2026-11-09T12:00:00"), resumed.startAt());
        // 元の記録は変更されない
        TimeEntry original = env.ctx.entries.find(first.id()).orElseThrow();
        assertEquals(TestEnv.jst("2026-11-09T11:00:00"), original.endAt());
        assertEquals(2, env.ctx.entries.listByDate(LocalDate.parse("2026-11-09")).size());
    }

    @Test
    void startRequiresCategoryAndTaskName() {
        assertThrows(ValidationException.class,
                () -> timer.start(new TaskSpec(null, null, "作業", null, Set.of())));
        assertThrows(ValidationException.class,
                () -> timer.start(new TaskSpec(null, env.categoryId, "  ", null, Set.of())));
        assertThrows(ValidationException.class,
                () -> timer.start(new TaskSpec("no-such-project", env.categoryId, "作業", null, Set.of())));
        assertTrue(timer.current().isEmpty());
    }

    @Test
    void failedSwitchLeavesRunningTimerUntouched() {
        TimeEntry a = timer.start(env.spec("作業A"));
        env.clock.advance(Duration.ofMinutes(10));
        assertThrows(ValidationException.class, () -> timer.start(env.spec("")));
        assertEquals(a.id(), timer.current().orElseThrow().id());
    }

    @Test
    void unfinishedEntryIsDetectedAfterRestart() {
        TimeEntry a = timer.start(env.spec("作業A"));
        env.close(); // 終了処理（停止）を行わずに落ちた想定

        try (AppContext reopened = new AppContext(dir.resolve("test.db"), env.clock)) {
            List<TimeEntry> unfinished = reopened.timer.findUnfinished();
            assertEquals(1, unfinished.size());
            assertEquals(a.id(), unfinished.get(0).id());
            assertNull(unfinished.get(0).endAt());
        }
    }

    @Test
    void unfinishedEntryCanBeFinishedNow() {
        TimeEntry a = timer.start(env.spec("作業A"));
        env.clock.advance(Duration.ofHours(3));
        timer.finishNow(a.id());
        assertEquals(TestEnv.jst("2026-11-09T13:00:00"), env.ctx.entries.find(a.id()).orElseThrow().endAt());
    }

    @Test
    void unfinishedEntryCanBeFinishedAtSpecifiedTime() {
        TimeEntry a = timer.start(env.spec("作業A"));
        env.clock.advance(Duration.ofHours(12));

        assertThrows(ValidationException.class, () -> timer.finishAt(a.id(), TestEnv.jst("2026-11-09T09:59:59")));
        assertThrows(ValidationException.class, () -> timer.finishAt(a.id(), TestEnv.jst("2026-11-09T22:00:01")));
        assertThrows(ValidationException.class, () -> timer.finishAt(a.id(), null));

        timer.finishAt(a.id(), TestEnv.jst("2026-11-09T12:15:00"));
        assertEquals(TestEnv.jst("2026-11-09T12:15:00"), env.ctx.entries.find(a.id()).orElseThrow().endAt());
        assertTrue(timer.findUnfinished().isEmpty());
        // 終了済みの記録には使えない
        assertThrows(ValidationException.class, () -> timer.finishNow(a.id()));
    }

    @Test
    void unfinishedEntryCanBeDiscarded() {
        TimeEntry a = timer.start(env.spec("作業A"));
        timer.discard(a.id());
        assertTrue(env.ctx.entries.find(a.id()).isEmpty());
        assertTrue(timer.findUnfinished().isEmpty());
    }
}
