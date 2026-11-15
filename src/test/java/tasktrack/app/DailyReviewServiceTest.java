package tasktrack.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tasktrack.domain.DailyReview;

class DailyReviewServiceTest {
    @TempDir
    Path dir;
    TestEnv env;
    DailyReviewService service;

    @BeforeEach
    void setUp() {
        env = new TestEnv(dir);
        service = env.ctx.reviews;
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    void savesOneReviewPerDayAndAllowsPartialInput() {
        LocalDate past = LocalDate.parse("2026-10-30");
        DailyReview created = service.save(past, new ReviewInput("設計書を完成", "", null, "", "")).orElseThrow();
        assertEquals("設計書を完成", created.achievements());
        assertEquals("", created.blockers());
        assertEquals("", created.decisions());

        env.clock.advance(Duration.ofHours(1));
        DailyReview updated = service.save(past, new ReviewInput("設計書を完成", "レビュー待ち", "", "", "")).orElseThrow();
        assertEquals(created.id(), updated.id());
        assertEquals(created.createdAt(), updated.createdAt());
        assertEquals(env.clock.instant(), updated.updatedAt());
        assertEquals("レビュー待ち", service.find(past).orElseThrow().blockers());

        assertTrue(service.find(LocalDate.parse("2026-10-31")).isEmpty());
        assertEquals(1, env.ctx.db.queryOne("SELECT count(*) FROM daily_review", rs -> rs.getInt(1)).orElseThrow());
    }

    @Test
    void savingAllBlankRemovesReviewAndNeverCreatesEmptyOne() {
        LocalDate day = LocalDate.parse("2026-11-09");
        assertTrue(service.save(day, new ReviewInput("", " ", null, "", "")).isEmpty());
        assertTrue(service.find(day).isEmpty());

        service.save(day, new ReviewInput("", "", "", "", "気づき"));
        assertTrue(service.save(day, new ReviewInput("", "", "", "", "")).isEmpty());
        assertTrue(service.find(day).isEmpty());
    }
}
