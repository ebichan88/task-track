package tasktrack.app;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

import tasktrack.AppContext;

/** テスト用: 一時ファイルの DB と、進められる時計を持つアプリ一式。 */
final class TestEnv implements AutoCloseable {

    /** 手動で進められる時計。 */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant t) {
            now = t;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return AppTime.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final MutableClock clock = new MutableClock(jst("2026-11-09T10:00:00"));
    final AppContext ctx;
    final String categoryId;

    TestEnv(Path dir) {
        ctx = new AppContext(dir.resolve("test.db"), clock);
        categoryId = ctx.masters.categories().get(0).id();
    }

    /** 日本時間のローカル日時文字列を Instant にする。 */
    static Instant jst(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(AppTime.ZONE).toInstant();
    }

    TaskSpec spec(String name) {
        return new TaskSpec(null, categoryId, name, null, Set.of());
    }

    EntryInput manual(String name, String start, String end) {
        return new EntryInput(spec(name), jst(start), jst(end));
    }

    @Override
    public void close() {
        ctx.close();
    }
}
