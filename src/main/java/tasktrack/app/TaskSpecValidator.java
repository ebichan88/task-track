package tasktrack.app;

import tasktrack.db.MasterRepository;
import tasktrack.db.TagRepository;
import tasktrack.domain.Category;
import tasktrack.domain.Project;

/** 作業内容の入力値を検証・正規化する。 */
public final class TaskSpecValidator {
    static final int MAX_NAME_LENGTH = 200;

    private final MasterRepository<Project> projects;
    private final MasterRepository<Category> categories;
    private final TagRepository tags;

    public TaskSpecValidator(MasterRepository<Project> projects, MasterRepository<Category> categories,
                             TagRepository tags) {
        this.projects = projects;
        this.categories = categories;
        this.tags = tags;
    }

    /** 検証し、前後の空白除去・空文字の null 化を行った値を返す。 */
    public TaskSpec validate(TaskSpec spec) {
        String projectId = blankToNull(spec.projectId());
        if (projectId != null && projects.findById(projectId).isEmpty()) {
            throw new ValidationException("指定されたプロジェクトが存在しません。");
        }
        String categoryId = blankToNull(spec.categoryId());
        if (categoryId == null) {
            throw new ValidationException("カテゴリを選択してください。");
        }
        if (categories.findById(categoryId).isEmpty()) {
            throw new ValidationException("指定されたカテゴリが存在しません。");
        }
        String taskName = blankToNull(spec.taskName());
        if (taskName == null) {
            throw new ValidationException("作業名を入力してください。");
        }
        if (taskName.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("作業名は" + MAX_NAME_LENGTH + "文字以内で入力してください。");
        }
        for (String tagId : spec.tagIds()) {
            if (tags.findById(tagId).isEmpty()) {
                throw new ValidationException("指定されたタグが存在しません。");
            }
        }
        return new TaskSpec(projectId, categoryId, taskName, blankToNull(spec.description()), spec.tagIds());
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
