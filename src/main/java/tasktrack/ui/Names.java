package tasktrack.ui;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import tasktrack.app.MasterService;
import tasktrack.domain.MasterItem;
import tasktrack.domain.Tag;

/** ID から表示名を引くためのスナップショット。無効化されたマスタは「（無効）」を付けて表示する。 */
final class Names {
    private final Map<String, String> projects;
    private final Map<String, String> categories;
    private final Map<String, String> tags;

    Names(MasterService masters) {
        projects = masters.projects().stream().collect(Collectors.toMap(MasterItem::id, Names::label));
        categories = masters.categories().stream().collect(Collectors.toMap(MasterItem::id, Names::label));
        tags = masters.tags().stream().collect(Collectors.toMap(Tag::id, Tag::name));
    }

    static String label(MasterItem item) {
        return item.active() ? item.name() : item.name() + "（無効）";
    }

    String project(String id) {
        return id == null ? "" : projects.getOrDefault(id, "（不明）");
    }

    String category(String id) {
        return categories.getOrDefault(id, "（不明）");
    }

    String tags(Collection<String> ids) {
        return ids.stream().map(id -> tags.getOrDefault(id, "（不明）")).sorted().collect(Collectors.joining(", "));
    }
}
