package tasktrack.app;

import java.time.LocalDate;

/** 期間指定（開始日・終了日とも含む）の検証。 */
final class Period {
    private Period() {
    }

    static void check(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("開始日と終了日を指定してください。");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("終了日は開始日以降を指定してください。");
        }
    }
}
