package tasktrack.app;

import java.util.Set;

import tasktrack.domain.TimeEntry;

/**
 * 作業の内容（何の作業か）を表す入力値。
 *
 * @param projectId   任意（null 可）
 * @param categoryId  必須
 * @param taskName    必須
 * @param description 任意（null 可）
 * @param tagIds      任意（空可）
 */
public record TaskSpec(String projectId, String categoryId, String taskName, String description,
                       Set<String> tagIds) {

    public TaskSpec {
        tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
    }

    public static TaskSpec of(TimeEntry e) {
        return new TaskSpec(e.projectId(), e.categoryId(), e.taskName(), e.description(), e.tagIds());
    }
}
