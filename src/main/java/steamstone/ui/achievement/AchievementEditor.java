package steamstone.ui.achievement;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.RequiredArgsConstructor;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;
import steamstone.model.Game;
import steamstone.model.achievement.Achievement;
import steamstone.model.achievement.AchievementList;
import steamstone.model.achievement.AchievementPreview;
import steamstone.model.achievement.AchievementState;
import steamstone.steam.SteamWebApi;
import steamstone.steam.nativeapi.NativeSteam;
import steamstone.steam.nativeapi.protocol.Request;
import steamstone.steam.nativeapi.protocol.Response;
import steamstone.steam.nativeapi.protocol.Status;
import steamstone.ui.task.StoneTask;
import steamstone.ui.scroll.SmoothListViewSkin;

/**
 * Stages edits locally and commits through an isolated, account-checked Steam worker.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class AchievementEditor extends VBox implements AutoCloseable {
    private final SteamWebApi api;
    private final ExecutorService requests = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("steamstone-achievements").factory());
    private final Label title = label("Choose a game", "editor-game-name");
    private final Label subtitle = label("Select a game from the sidebar", "editor-game-id");
    private final Label notice = label("", "achievement-notice");
    private final Label empty = label("Select a game to load its achievements.", "editor-empty-description");
    private final ListView<Achievement> list = new ListView<>();
    private final Button unlock = button("Unlock all", "unlock-all");
    private final Button lock = button("Lock all", "lock-all");
    private final Button reset = button("Reset", "reset-preview");
    private final Button commit = button("Commit", "commit-preview");
    private final Button reload = button("↻", "reload-achievements");
    private final Map<String, Image> icons = new LinkedHashMap<>(32, .75f, true);
    private AchievementPreview preview = new AchievementPreview(List.of());
    private Game game;
    private String key, steamId;

    private final NativeSteam nativeSteam;
    private final Provider<AchievementCell> cells;
    private final ReadOnlyBooleanWrapper committing = new ReadOnlyBooleanWrapper(false);
    private boolean nativeReady;
    private Task<LoadedAchievements> task;
    private Task<Response> commitTask;
    private String dataNotice = "";

    @Inject
    private void initialize() {
        getStyleClass().add("editor-page");
        title.setId("editor-game-name");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinWidth(0);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        final var heading = new VBox(2, title, subtitle);
        heading.setMinWidth(0);
        HBox.setHgrow(heading, Priority.ALWAYS);
        commit.getStyleClass().setAll("primary-button");
        reload.setTooltip(new Tooltip("Reload achievements"));
        reset.setTooltip(new Tooltip("Reset local changes"));
        final var header = new HBox(7, heading, reload, unlock, lock, reset, commit);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("editor-header");
        notice.setWrapText(true);
        notice.setId("achievement-notice");
        notice.visibleProperty().bind(notice.textProperty().isNotEmpty());
        notice.managedProperty().bind(notice.visibleProperty());
        empty.setWrapText(true);
        empty.setMaxWidth(420);
        list.setId("achievement-list");
        list.getStyleClass().add("achievement-list");
        list.setFixedCellSize(56);
        list.setPlaceholder(empty);
        list.setCellFactory((final var view) -> cells.get());
        list.setSkin(new SmoothListViewSkin<>(list));
        final var body = new VBox(6, notice, list);
        body.getStyleClass().add("achievement-body");
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(header, new Separator(), body);
        unlock.setOnAction((final var e) -> {
            preview.setAll(true);
            changed();
        });
        lock.setOnAction((final var e) -> {
            preview.setAll(false);
            changed();
        });
        reset.setOnAction((final var e) -> {
            preview.reset();
            changed();
        });
        commit.setOnAction((final var e) -> commit());
        reload.setOnAction((final var e) -> {
            if (discardPendingIfNeeded())
                load();
        });
        updateControls();
    }

    public void open(final Game next, final String apiKey, final String account) {
        if (next != null && game != null && next.appId() == game.appId()
                && Objects.equals(key, apiKey) && Objects.equals(steamId, account))
            return;
        cancel();
        game = next;
        key = apiKey;
        steamId = account;
        title.setText(next == null ? "Choose a game" : next.name());
        title.setTooltip(next == null ? null : new Tooltip(next.name()));
        subtitle.setText(next == null ? "Select a game from the sidebar" : "APP " + next.appId());
        if (next == null) {
            clear();
            empty.setText("Select a game to load its achievements.");
        } else
            load();
    }

    private void load() {
        cancel();
        clear();
        if (game == null)
            return;
        empty.setText("Loading achievements from Steam…");
        final var appId = game.appId();
        final var requestKey = key;
        final var account = steamId;
        final var next = new StoneTask<>(() -> {
            final var web = api.loadAchievements(requestKey, account, appId);
            if (Thread.currentThread().isInterrupted()) {
                throw new StoneException(ErrorCode.CANCELLED);
            }
            if (web.achievements().isEmpty()) {
                return new LoadedAchievements(web, false);
            }
            final Response response;
            try {
                response = nativeSteam.execute(new Request(appId, account,
                        web.achievements().stream().map(Achievement::id).toList(), Map.of(), Map.of()));
            } catch (final StoneException e) {
                if (Thread.currentThread().isInterrupted() || e.code() == ErrorCode.CANCELLED) {
                    throw new StoneException(ErrorCode.CANCELLED, e);
                }
                return new LoadedAchievements(new AchievementList(web.achievements(), e.code()), false);
            }
            return response.status() == Status.READ
                    ? new LoadedAchievements(merge(web.achievements(), response, ErrorCode.NONE), true)
                    : new LoadedAchievements(new AchievementList(web.achievements(), response.errorCode()), false);
        });
        task = next;
        updateControls();
        next.setOnSucceeded((final var e) -> {
            if (task != next)
                return;
            task = null;
            nativeReady = next.getValue().nativeReady();
            final var result = next.getValue().data();
            preview = new AchievementPreview(result.achievements());
            dataNotice = result.notice();
            list.getItems().setAll(result.achievements());
            subtitle.setText("APP " + appId + "  ·  " + result.achievements().size() + " achievements");
            empty.setText("Steam returned no achievement definitions for this game.");
            changed();
        });
        next.setOnFailed((final var e) -> {
            if (task != next)
                return;
            task = null;
            empty.setText("Achievements couldn't be loaded. Use Reload to try again.");
            notice.setText(StoneException.codeOf(next.getException()).message());
            updateControls();
        });
        requests.submit(next);
    }

    public AchievementPreview preview() {
        return preview;
    }

    public boolean nativeReady() {
        return nativeReady;
    }

    private void clear() {
        nativeReady = false;
        preview = new AchievementPreview(List.of());
        list.getItems().clear();
        icons.clear();
        dataNotice = "";
        changed();
    }

    public void changed() {
        notice.setText(dataNotice);
        list.refresh();
        updateControls();
    }

    private void updateControls() {
        final var editable = nativeReady && task == null && !committing.get() && preview.achievements().stream()
                .anyMatch((final var a) -> a.state() != AchievementState.UNKNOWN);
        unlock.setDisable(!editable);
        lock.setDisable(!editable);
        reset.setDisable(!editable || preview.changeCount() == 0);
        commit.setDisable(!editable || preview.changeCount() == 0);
        reload.setDisable(game == null || task != null || committing.get());
        list.setDisable(committing.get());
        commit.setText(committing.get() ? "Saving…" : "Commit");
        commit.setTooltip(preview.changeCount() == 0 ? null :
                new Tooltip("Commit " + preview.changeCount() + " pending change(s)"));
    }

    public ReadOnlyBooleanProperty committingProperty() {
        return committing.getReadOnlyProperty();
    }

    public boolean discardPendingIfNeeded() {
        if (committing.get())
            return false;
        if (preview.changeCount() == 0)
            return true;
        final var alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Discard " + preview.changeCount() + " pending achievement change(s)?",
                ButtonType.CANCEL, new ButtonType("Discard", ButtonBar.ButtonData.OK_DONE));
        alert.setHeaderText("Unsaved changes");
        if (getScene() != null)
            alert.initOwner(getScene().getWindow());
        return alert.showAndWait().map((final var button) -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .orElse(false);
    }

    public boolean isCurrentGame(final Game candidate) {
        return game != null && candidate.appId() == game.appId();
    }

    private void commit() {
        if (!nativeReady || committing.get() || preview.changeCount() == 0)
            return;
        final var desired = preview.changes();
        final var expected = new LinkedHashMap<String, Boolean>();
        for (final var achievement : preview.achievements()) {
            if (desired.containsKey(achievement.id()))
                expected.put(achievement.id(), achievement.state() == AchievementState.UNLOCKED);
        }
        final var request = new Request(game.appId(), steamId,
                preview.achievements().stream().map(Achievement::id).toList(), expected, desired);
        committing.set(true);
        dataNotice = "Saving changes to Steam…";
        changed();
        final var next = new StoneTask<>(() -> nativeSteam.execute(request));
        commitTask = next;
        next.setOnSucceeded((final var e) -> {
            commitTask = null;
            committing.set(false);
            final var response = next.getValue();
            if (response.status() == Status.UNKNOWN)
                nativeReady = false;
            else if (!response.achievements().isEmpty()) {
                final var snapshot = merge(preview.achievements(), response, response.errorCode());
                preview = new AchievementPreview(snapshot.achievements());
                // Successful readbacks disappear from pending; rejected/unmatched intentions remain.
                for (final var achievement : preview.achievements()) {
                    if (desired.containsKey(achievement.id()))
                        preview.setUnlocked(achievement, desired.get(achievement.id()));
                }
                list.getItems().setAll(snapshot.achievements());
            }
            dataNotice = response.message();
            changed();
        });
        next.setOnFailed((final var e) -> {
            commitTask = null;
            committing.set(false);
            nativeReady = false;
            dataNotice = ErrorCode.STEAM_STORAGE.message();
            changed();
        });
        requests.submit(next);
    }

    private static AchievementList merge(final List<Achievement> source, final Response response,
                                         final ErrorCode errorCode) {
        return new AchievementList(source.stream().map((final var achievement) -> {
            final var entry = response.achievements().get(achievement.id());
            if (entry == null)
                return new Achievement(achievement.id(), achievement.name(), achievement.description(),
                        AchievementState.UNKNOWN, 0, achievement.icon(), achievement.lockedIcon());
            final var description = entry.description().isBlank() ? achievement.description() : entry.description();
            return new Achievement(achievement.id(), achievement.name(), description, entry.state(), entry.unlockTime(),
                    achievement.icon(), achievement.lockedIcon());
        }).toList(), errorCode);
    }

    public Image icon(final Achievement achievement) {
        final var url = preview.isUnlocked(achievement) ? achievement.icon() : achievement.lockedIcon();
        if (url.isBlank())
            return null;
        try {
            final var uri = URI.create(url);
            final var host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || !(host.endsWith(".steamstatic.com") || host.endsWith(".steamcommunity.com")
                    || host.equals("steamcdn-a.akamaihd.net")))
                return null;
        } catch (final IllegalArgumentException e) {
            return null;
        }
        final var image = icons.computeIfAbsent(url, (final var value) -> new Image(value, 32, 32, true, true, true));
        if (icons.size() > 100) {
            icons.remove(icons.keySet().iterator().next());
        }
        return image;
    }

    private void cancel() {
        if (task != null)
            task.cancel(true);
        task = null;
    }

    @Override
    public void close() {
        cancel();
        key = null;
        steamId = null;
        if (commitTask == null)
            requests.shutdownNow();
        else
            requests.shutdown();
        icons.clear();
    }

    public static Label label(final String text, final String style) {
        final var label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }

    private static Button button(final String text, final String id) {
        final var button = new Button(text);
        button.setId(id);
        button.getStyleClass().add("secondary-button");
        return button;
    }
}
