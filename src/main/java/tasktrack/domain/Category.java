package tasktrack.domain;

/** カテゴリマスタ。 */
public record Category(String id, String name, String description, boolean active) implements MasterItem {
}
