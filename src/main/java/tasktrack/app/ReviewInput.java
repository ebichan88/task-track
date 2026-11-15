package tasktrack.app;

/** 日次振り返りの入力値（すべて任意）。 */
public record ReviewInput(String achievements, String blockers, String decisions, String nextActions,
                          String reflections) {

    public ReviewInput {
        achievements = clean(achievements);
        blockers = clean(blockers);
        decisions = clean(decisions);
        nextActions = clean(nextActions);
        reflections = clean(reflections);
    }

    public boolean isEmpty() {
        return achievements.isEmpty() && blockers.isEmpty() && decisions.isEmpty() && nextActions.isEmpty()
                && reflections.isEmpty();
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }
}
