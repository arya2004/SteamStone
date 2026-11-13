package steamstone.ui.verification;

import com.google.inject.util.Modules;

import java.util.concurrent.atomic.AtomicReference;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ScrollEvent.HorizontalTextScrollUnits;
import javafx.scene.input.ScrollEvent.VerticalTextScrollUnits;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;
import steamstone.inject.SavedConnectionFixtureModule;
import steamstone.inject.SteamApiFixtureModule;
import steamstone.inject.SteamStoneInjector;
import steamstone.inject.SteamStoneModule;
import steamstone.settings.ConnectionStore;

import java.util.UUID;

import javafx.event.EventType;
import steamstone.ui.library.LibraryView;
import steamstone.ui.scroll.SmoothListViewSkin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optional desktop-only fixture check. Never uses real keys, the user's keyring, or a Steam session.
 */
public final class FeatureCheckApplication extends Application {
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String FIRST_ID = "76561198000000000";
    private static final String SECOND_ID = "76561198000000001";

    private final AtomicReference<String> openedLink = new AtomicReference<>();
    private SavedLoginApiFixture api;
    private final String service = "steamstone.feature-check." + UUID.randomUUID();
    private ConnectionStore store;
    private LibraryView view;
    private Stage stage;
    private Timeline checks;
    private int step;
    private int ticks;
    private int requestsBeforeRestart;
    private int scrollTick;
    private ListView<String> games;
    private ListView<String> achievements;
    private double nativePosition;

    @Override
    public void start(final Stage stage) {
        this.stage = stage;
        api = new SavedLoginApiFixture();
        store = new ConnectionStore(service);
        restart();
        stage.setTitle("SteamStone — saved login and scrolling verification");
        stage.show();
        checks = new Timeline(new KeyFrame(Duration.millis(100), (final var event) -> check()));
        checks.setCycleCount(Timeline.INDEFINITE);
        checks.play();
    }

    private void restart() {
        if (view != null)
            view.close();
        view = SteamStoneInjector.create(Modules.override(new SteamStoneModule()).with(
                        new SteamApiFixtureModule(api.base()), new SavedConnectionFixtureModule(store)))
                .getInstance(LibraryView.class);
        view.openLinksWith(openedLink::set);
        final var scene = new Scene(view, 1180, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        stage.setScene(scene);
        assertTrue(view.lookup("#restoring-connection").isVisible());
        assertFalse(view.lookup(".connection-page").isVisible());
    }

    private boolean loaded(final String name) {
        return ((Label) view.lookup("#active-account")).getText().equals(name)
                && !((Button) view.lookup("#load-library")).isDisabled();
    }

    private void check() {
        try {
            assertTrue(++ticks < 200, "Feature check timed out at step " + step);
            switch (step) {
                case 0 -> {
                    if (((Button) view.lookup("#load-library")).isDisabled())
                        return;
                    assertFalse(((CheckBox) view.lookup("#remember-account")).isSelected());
                    ((Hyperlink) view.lookup("#get-api-key")).fire();
                    assertEquals("https://steamcommunity.com/dev/apikey", openedLink.get());
                    ((PasswordField) view.lookup("#api-key")).setText(KEY);
                    ((TextField) view.lookup("#steam-profile")).setText(FIRST_ID);
                    ((CheckBox) view.lookup("#remember-account")).fire();
                    ((Button) view.lookup("#load-library")).fire();
                    step++;
                }
                case 1 -> {
                    if (!loaded("First"))
                        return;
                    assertTrue(store.saved().isPresent());
                    assertEquals(KEY, store.saved().orElseThrow().apiKey());
                    assertEquals(null, view.lookup("#account-menu"));
                    requestsBeforeRestart = api.requests.get();
                    restart();
                    step++;
                }
                case 2 -> {
                    if (!loaded("First"))
                        return;
                    assertTrue(api.requests.get() > requestsBeforeRestart);
                    assertFalse(view.lookup(".connection-page").isVisible());
                    assertTrue(((CheckBox) view.lookup("#remember-account")).isSelected());
                    ((Button) view.lookup("#disconnect")).fire();
                    ((CheckBox) view.lookup("#remember-account")).fire();
                    step++;
                }
                case 3 -> {
                    if (((Button) view.lookup("#load-library")).isDisabled())
                        return;
                    assertTrue(store.saved().isEmpty());

                    ((PasswordField) view.lookup("#api-key")).setText(KEY);
                    ((TextField) view.lookup("#steam-profile")).setText(SECOND_ID);
                    ((Button) view.lookup("#load-library")).fire();
                    step++;
                }
                case 4 -> {
                    if (!loaded("Second"))
                        return;
                    assertTrue(store.saved().isEmpty());
                    store.remember(KEY, FIRST_ID);
                    api.status = 503;
                    restart();
                    step++;
                }
                case 5 -> {
                    if (!view.lookup(".connection-page").isVisible())
                        return;
                    assertEquals(ErrorCode.STEAM_API.message(), ((Label) view.lookup(".error-message")).getText());
                    view.close();
                    games = scrollList(52);
                    achievements = scrollList(56);
                    stage.setScene(new Scene(new HBox(games, achievements), 900, 600));
                    games.applyCss();
                    games.layout();
                    achievements.applyCss();
                    achievements.layout();
                    fire(games, ScrollEvent.SCROLL, -40, VerticalTextScrollUnits.LINES, -3, false);
                    fire(achievements, ScrollEvent.SCROLL, -40, VerticalTextScrollUnits.LINES, -3, false);
                    scrollTick = ticks;
                    step++;
                }
                case 6 -> {
                    if (ticks - scrollTick < 3)
                        return;
                    assertTrue(flow(games).getPosition() > 0);
                    assertTrue(flow(achievements).getPosition() > 0);
                    fire(games, ScrollEvent.SCROLL_STARTED, 0, VerticalTextScrollUnits.NONE, 0, false);
                    fire(games, ScrollEvent.SCROLL, -7.5, VerticalTextScrollUnits.NONE, 0, false);
                    fire(games, ScrollEvent.SCROLL_FINISHED, 0, VerticalTextScrollUnits.NONE, 0, false);
                    fire(games, ScrollEvent.SCROLL, -7.5, VerticalTextScrollUnits.NONE, 0, true);
                    nativePosition = flow(games).getPosition();
                    scrollTick = ticks;
                    step++;
                }
                case 7 -> {
                    if (ticks - scrollTick < 3)
                        return;
                    assertEquals(nativePosition, flow(games).getPosition(), 0.000001);
                    checks.stop();
                    System.out.println(
                            "Feature check passed: OS-only opt-in save, auto-login, forget, session-only " +
                                    "login, failure fallback, key link, wheel animation and native inertia.");
                    Platform.exit();
                }
                default -> throw new StoneException(ErrorCode.APPLICATION);
            }
        } catch (final Throwable e) {
            System.err.println("Feature check failed at step " + step);
            e.printStackTrace();
            stop();
            Platform.exit();
            System.exit(1);
        }
    }

    private ListView<String> scrollList(final double cellSize) {
        final var list = new ListView<String>();
        list.setFixedCellSize(cellSize);
        for (var row = 0; row < 300; row++)
            list.getItems().add("Fixture row " + row);
        list.setSkin(new SmoothListViewSkin<>(list));
        list.setPrefSize(440, 580);
        return list;
    }

    private VirtualFlow<?> flow(final ListView<?> list) {
        return (VirtualFlow<?>) list.lookup(".virtual-flow");
    }

    private void fire(final ListView<?> list, final EventType<ScrollEvent> type, final double delta,
                      final VerticalTextScrollUnits units, final double lines, final boolean inertia) {
        Event.fireEvent(list, new ScrollEvent(type, 10, 10, 10, 10, false, false, false, false,
                false, inertia, 0, delta, 0, delta, HorizontalTextScrollUnits.NONE, 0, units, lines, 0,
                new PickResult(list, 10, 10)));
    }

    @Override
    public void stop() {
        if (checks != null)
            checks.stop();
        if (view != null)
            view.close();
        if (api != null)
            api.close();
        if (store != null)
            store.forget();
    }
}
