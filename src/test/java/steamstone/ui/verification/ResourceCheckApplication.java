package steamstone.ui.verification;

import com.google.inject.util.Modules;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;
import steamstone.inject.SavedConnectionFixtureModule;
import steamstone.inject.SteamApiFixtureModule;
import steamstone.inject.SteamStoneInjector;
import steamstone.inject.SteamStoneModule;
import steamstone.settings.ConnectionStore;
import steamstone.ui.library.LibraryView;

import java.lang.management.ManagementFactory;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducible local workload; no Steam session, live credentials, or remote images.
 */
public final class ResourceCheckApplication extends Application {
    private static final int GAME_COUNT = 5000;
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String PROFILE = "76561198000000000";
    private SavedLoginApiFixture api;
    private ConnectionStore store;
    private LibraryView view;
    private Timeline poll;
    private int tick;
    private int step;
    private int idleAt;

    @Override
    public void start(final Stage stage) {
        api = new SavedLoginApiFixture(GAME_COUNT);
        store = new ConnectionStore("steamstone.resources." + UUID.randomUUID());
        view = SteamStoneInjector.create(Modules.override(new SteamStoneModule()).with(
                        new SavedConnectionFixtureModule(store), new SteamApiFixtureModule(api.base())))
                .getInstance(LibraryView.class);
        final var scene = new Scene(view, 1180, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
        poll = new Timeline(new KeyFrame(Duration.millis(100), (final var event) -> check()));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
    }

    private void check() {
        try {
            assertTrue(++tick < 400, "Resource check timed out at step " + step);
            final var games = (ListView<?>) view.lookup("#game-list");
            switch (step) {
                case 0 -> {
                    if (((Button) view.lookup("#load-library")).isDisabled())
                        return;
                    ((PasswordField) view.lookup("#api-key")).setText(KEY);
                    ((TextField) view.lookup("#steam-profile")).setText(PROFILE);
                    ((Button) view.lookup("#load-library")).fire();
                    step++;
                }
                case 1 -> {
                    if (games.getItems().isEmpty() || ((Button) view.lookup("#reload-achievements")).isDisabled())
                        return;
                    assertEquals(GAME_COUNT, games.getItems().size());
                    // Stress cancellation without ever loading native achievement definitions.
                    for (var index = 1; index < 100; index++)
                        games.getSelectionModel().select(index);
                    ((TextField) view.lookup("#search-games")).setText("Game 1");
                    ((TextField) view.lookup("#search-games")).clear();
                    idleAt = tick;
                    step++;
                }
                case 2 -> {
                    if (tick - idleAt < 100 || ((Button) view.lookup("#reload-achievements")).isDisabled())
                        return;
                    report("loaded-idle");
                    ((Button) view.lookup("#disconnect")).fire();
                    assertTrue(games.getItems().isEmpty());
                    assertTrue(((ListView<?>) view.lookup("#achievement-list")).getItems().isEmpty());
                    view.close();
                    idleAt = tick;
                    step++;
                }
                case 3 -> {
                    if (tick - idleAt < 30)
                        return;
                    report("closed");
                    poll.stop();
                    Platform.exit();
                }
                default -> throw new AssertionError("Unexpected resource check step");
            }
        } catch (final Throwable e) {
            e.printStackTrace();
            stop();
            System.exit(1);
        }
    }

    private static void report(final String phase) {
        // Measurement only: do not force GC in the actual application.
        System.gc();
        final var threads = Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName).sorted().toList();
        final var owned = threads.stream().filter((final var name) -> name.startsWith("steamstone-")
                || name.startsWith("HttpClient-")).toList();
        System.out.println(phase + ": heapBytes=" + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()
                + ", platformThreads=" + threads.size() + ", appAndHttpThreads=" + owned);
    }

    @Override
    public void stop() {
        if (poll != null)
            poll.stop();
        if (view != null)
            view.close();
        if (store != null)
            store.forget();
        if (api != null)
            api.close();
    }
}
