package tasktrack.domain;

/** プロジェクトマスタ。 */
public record Project(String id, String name, String description, boolean active) implements MasterItem {
}
