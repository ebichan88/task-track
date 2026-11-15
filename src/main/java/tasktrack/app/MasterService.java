package tasktrack.app;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tasktrack.db.MasterRepository;
import tasktrack.db.TagRepository;
import tasktrack.domain.Category;
import tasktrack.domain.MasterItem;
import tasktrack.domain.Project;
import tasktrack.domain.Tag;

/** プロジェクト・カテゴリ・タグのマスタ管理。 */
public final class MasterService {
    private static final int MAX_NAME_LENGTH = 100;

    private final MasterRepository<Project> projects;
    private final MasterRepository<Category> categories;
    private final TagRepository tags;

    public MasterService(MasterRepository<Project> projects, MasterRepository<Category> categories,
                         TagRepository tags) {
        this.projects = projects;
        this.categories = categories;
        this.tags = tags;
    }

    // --- プロジェクト -----------------------------------------------------------

    /** 無効なものも含む全プロジェクト（有効なものが先）。 */
    public List<Project> projects() {
        return projects.findAll();
    }

    public Project createProject(String name, String description) {
        Project p = new Project(newId(), checkName(projects, null, name, "プロジェクト名"), text(description), true);
        projects.insert(p);
        return p;
    }

    public Project updateProject(String id, String name, String description, boolean active) {
        requireExists(projects.findById(id), "プロジェクト");
        Project p = new Project(id, checkName(projects, id, name, "プロジェクト名"), text(description), active);
        projects.update(p);
        return p;
    }

    // --- カテゴリ ---------------------------------------------------------------

    /** 無効なものも含む全カテゴリ（有効なものが先）。 */
    public List<Category> categories() {
        return categories.findAll();
    }

    public Category createCategory(String name, String description) {
        Category c = new Category(newId(), checkName(categories, null, name, "カテゴリ名"), text(description), true);
        categories.insert(c);
        return c;
    }

    public Category updateCategory(String id, String name, String description, boolean active) {
        requireExists(categories.findById(id), "カテゴリ");
        Category c = new Category(id, checkName(categories, id, name, "カテゴリ名"), text(description), active);
        categories.update(c);
        return c;
    }

    // --- タグ -------------------------------------------------------------------

    public List<Tag> tags() {
        return tags.findAll();
    }

    public Tag createTag(String name) {
        Tag t = new Tag(newId(), checkTagName(null, name));
        tags.insert(t);
        return t;
    }

    public Tag renameTag(String id, String name) {
        requireExists(tags.findById(id), "タグ");
        Tag t = new Tag(id, checkTagName(id, name));
        tags.update(t);
        return t;
    }

    /** タグを削除する。作業記録からもそのタグが外れる。 */
    public void deleteTag(String id) {
        tags.delete(id);
    }

    // --- 共通 -------------------------------------------------------------------

    private static <T extends MasterItem> String checkName(MasterRepository<T> repo, String selfId, String name,
                                                           String label) {
        String n = normalizeName(name, label);
        Optional<T> same = repo.findByName(n);
        if (same.isPresent() && !same.get().id().equals(selfId)) {
            throw new ValidationException("同じ" + label + "が既に登録されています: " + n);
        }
        return n;
    }

    private String checkTagName(String selfId, String name) {
        String n = normalizeName(name, "タグ名");
        Optional<Tag> same = tags.findByName(n);
        if (same.isPresent() && !same.get().id().equals(selfId)) {
            throw new ValidationException("同じタグ名が既に登録されています: " + n);
        }
        return n;
    }

    private static String normalizeName(String name, String label) {
        if (name == null || name.isBlank()) {
            throw new ValidationException(label + "を入力してください。");
        }
        String n = name.strip();
        if (n.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(label + "は" + MAX_NAME_LENGTH + "文字以内で入力してください。");
        }
        return n;
    }

    private static void requireExists(Optional<?> item, String label) {
        if (item.isEmpty()) {
            throw new ValidationException("対象の" + label + "が見つかりません。");
        }
    }

    private static String text(String s) {
        return s == null ? "" : s.strip();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
