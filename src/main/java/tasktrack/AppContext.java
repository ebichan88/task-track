package tasktrack;

import java.nio.file.Path;
import java.time.Clock;

import tasktrack.app.BackupService;
import tasktrack.app.DailyReviewService;
import tasktrack.app.ExportService;
import tasktrack.app.MasterService;
import tasktrack.app.SummaryService;
import tasktrack.app.TaskSpecValidator;
import tasktrack.app.TimeEntryService;
import tasktrack.app.TimerService;
import tasktrack.db.AppStateRepository;
import tasktrack.db.DailyReviewRepository;
import tasktrack.db.Database;
import tasktrack.db.MasterRepository;
import tasktrack.db.TagRepository;
import tasktrack.db.TimeEntryRepository;
import tasktrack.domain.Category;
import tasktrack.domain.Project;

/** リポジトリとサービスを組み立てて保持する。 */
public final class AppContext implements AutoCloseable {
    public final Clock clock;
    public final Database db;
    public final AppStateRepository appState;
    public final TimerService timer;
    public final TimeEntryService entries;
    public final MasterService masters;
    public final SummaryService summary;
    public final DailyReviewService reviews;
    public final ExportService export;
    public final BackupService backup;

    public AppContext(Path dbFile, Clock clock) {
        this.clock = clock;
        this.db = Database.open(dbFile);
        MasterRepository<Project> projectRepo = MasterRepository.projects(db);
        MasterRepository<Category> categoryRepo = MasterRepository.categories(db);
        TagRepository tagRepo = new TagRepository(db);
        TimeEntryRepository entryRepo = new TimeEntryRepository(db);
        DailyReviewRepository reviewRepo = new DailyReviewRepository(db);
        TaskSpecValidator validator = new TaskSpecValidator(projectRepo, categoryRepo, tagRepo);

        this.appState = new AppStateRepository(db);
        this.timer = new TimerService(db, entryRepo, validator, clock);
        this.entries = new TimeEntryService(db, entryRepo, validator, clock);
        this.masters = new MasterService(projectRepo, categoryRepo, tagRepo);
        this.summary = new SummaryService(entryRepo, projectRepo, categoryRepo);
        this.reviews = new DailyReviewService(db, reviewRepo, clock);
        this.export = new ExportService(entryRepo, reviewRepo, projectRepo, categoryRepo, tagRepo);
        this.backup = new BackupService(db, clock);
    }

    @Override
    public void close() {
        db.close();
    }
}
