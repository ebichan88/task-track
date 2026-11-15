package tasktrack.domain;

/** プロジェクト・カテゴリに共通する「名前・説明・有効/無効」を持つマスタ。 */
public interface MasterItem {
    String id();

    String name();

    String description();

    boolean active();
}
