package tasktrack.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** 日時の扱いに関する共通定義。表示・集計・出力はすべて日本時間を基準とする。 */
public final class AppTime {
    /** 表示・集計の基準タイムゾーン。日付の境界はこのタイムゾーンの 00:00。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private static final DateTimeFormatter ISO_OFFSET =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxxx").withZone(ZONE);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZONE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZONE);
    private static final DateTimeFormatter MONTH_DAY_TIME =
            DateTimeFormatter.ofPattern("MM/dd HH:mm:ss").withZone(ZONE);

    private AppTime() {
    }

    /** 現在時刻（秒未満切り捨て）。DB には秒精度で保存する。 */
    public static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    /** その日の 00:00（日本時間）。 */
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    public static LocalDate dateOf(Instant t) {
        return LocalDate.ofInstant(t, ZONE);
    }

    /** 例: 2026-11-09T10:00:00+09:00 */
    public static String formatIso(Instant t) {
        return ISO_OFFSET.format(t);
    }

    /** 例: 2026-11-09 10:00:00 */
    public static String formatDateTime(Instant t) {
        return DATE_TIME.format(t);
    }

    /** {@code baseDate} と同じ日なら時刻のみ、異なる日なら月日付きで返す。 */
    public static String formatTimeOn(LocalDate baseDate, Instant t) {
        return dateOf(t).equals(baseDate) ? TIME.format(t) : MONTH_DAY_TIME.format(t);
    }

    /** 秒数を H:MM:SS 形式にする。 */
    public static String formatDuration(long seconds) {
        long s = Math.max(0, seconds);
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
