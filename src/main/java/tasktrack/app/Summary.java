package tasktrack.app;

import java.time.LocalDate;
import java.util.List;

/**
 * 集計結果。
 *
 * @param entryCount 期間内に掛かる作業記録の件数
 */
public record Summary(LocalDate from, LocalDate to, long totalSeconds, int entryCount,
                      List<Row> daily, List<Row> weekly, List<Row> byCategory, List<Row> byProject) {

    /** 集計の1行。{@code entryCount} はその行に掛かる作業記録の件数。 */
    public record Row(String label, long seconds, int entryCount) {
    }
}
