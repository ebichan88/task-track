package tasktrack.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tasktrack.domain.Category;
import tasktrack.domain.Project;
import tasktrack.domain.Tag;
import tasktrack.domain.TimeEntry;

class MasterServiceTest {
    @TempDir
    Path dir;
    TestEnv env;
    MasterService masters;

    @BeforeEach
    void setUp() {
        env = new TestEnv(dir);
        masters = env.ctx.masters;
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    void initialCategoriesAreSeeded() {
        List<String> names = masters.categories().stream().map(Category::name).toList();
        assertEquals(8, names.size());
        assertTrue(names.containsAll(List.of("設計・技術調査", "実装・コーディング", "レビュー", "テスト・品質保証",
                "会議・コミュニケーション", "問い合わせ・障害対応", "プロジェクト管理", "その他")));
        assertTrue(masters.categories().stream().allMatch(Category::active));
    }

    @Test
    void projectCanBeCreatedEditedAndDeactivated() {
        Project p = masters.createProject(" 案件A ", "説明");
        assertEquals("案件A", p.name());
        assertTrue(p.active());

        masters.updateProject(p.id(), "案件A改", "新しい説明", false);
        Project saved = masters.projects().get(0);
        assertEquals("案件A改", saved.name());
        assertEquals("新しい説明", saved.description());
        assertFalse(saved.active());
    }

    @Test
    void categoryCanBeCreatedAndEdited() {
        Category c = masters.createCategory("学習", "自己学習");
        masters.updateCategory(c.id(), "学習・研修", "研修を含む", true);
        Category saved = masters.categories().stream().filter(x -> x.id().equals(c.id())).findFirst().orElseThrow();
        assertEquals("学習・研修", saved.name());
        assertEquals(9, masters.categories().size());
    }

    @Test
    void namesMustBePresentAndUnique() {
        masters.createProject("案件A", "");
        assertThrows(ValidationException.class, () -> masters.createProject("案件A", ""));
        assertThrows(ValidationException.class, () -> masters.createProject("  ", ""));
        assertThrows(ValidationException.class, () -> masters.createCategory("その他", ""));
        masters.createTag("調査");
        assertThrows(ValidationException.class, () -> masters.createTag("調査"));
        // 自分自身と同名での更新は可能
        Project p = masters.projects().get(0);
        masters.updateProject(p.id(), "案件A", "説明だけ変更", true);
    }

    @Test
    void multipleTagsCanBeAttachedAndDeletingTagDetachesIt() {
        Tag t1 = masters.createTag("AI利用");
        Tag t2 = masters.createTag("突発対応");
        TimeEntry e = env.ctx.entries.createManual(new EntryInput(
                new TaskSpec(null, env.categoryId, "障害調査", null, Set.of(t1.id(), t2.id())),
                TestEnv.jst("2026-11-08T13:00:00"), TestEnv.jst("2026-11-08T14:00:00")));
        assertEquals(Set.of(t1.id(), t2.id()), env.ctx.entries.find(e.id()).orElseThrow().tagIds());

        masters.renameTag(t1.id(), "AI活用");
        assertEquals(List.of("AI活用", "突発対応"), masters.tags().stream().map(Tag::name).sorted().toList());

        masters.deleteTag(t2.id());
        assertEquals(Set.of(t1.id()), env.ctx.entries.find(e.id()).orElseThrow().tagIds());
    }
}
