package steamstone.ui;

import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import steamstone.model.*;
import steamstone.steam.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Read-only Steam data with an explicitly local, disposable editing preview. */
public final class AchievementEditor extends VBox implements AutoCloseable {
    private final SteamWebApi api;
    private final ExecutorService requests = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("steamstone-achievements").factory());
    private final Label title = label("Choose a game", "editor-game-name");
    private final Label subtitle = label("Select a game from the sidebar", "editor-game-id");
    private final Label notice = label("", "achievement-notice");
    private final Label pending = label("0 preview changes", "muted");
    private final Label empty = label("Select a game to load its achievements.", "editor-empty-description");
    private final ListView<Achievement> list = new ListView<>();
    private final Button unlock = button("Unlock all", "unlock-all");
    private final Button lock = button("Lock all", "lock-all");
    private final Button reset = button("Reset preview", "reset-preview");
    private final Button commit = button("Commit", "commit-preview");
    private final Button reload = button("Reload", "reload-achievements");
    private final Map<String, Image> icons = new LinkedHashMap<>(32, .75f, true);
    private AchievementPreview preview = new AchievementPreview(java.util.List.of());
    private Game game;
    private String key, steamId;
    private Task<AchievementList> task;
    private String dataNotice = "";

    public AchievementEditor(SteamWebApi api) {
        this.api = api;
        getStyleClass().add("editor-page");
        title.setId("editor-game-name");
        title.setWrapText(true);
        var header = new VBox(10, label("ACHIEVEMENT EDITOR", "accent-eyebrow"), title, subtitle);
        header.getStyleClass().add("editor-header");
        var warning = label("PREVIEW ONLY · Changes stay in this window. Nothing is sent to Steam.", "preview-banner");
        warning.setWrapText(true);
        var space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        commit.getStyleClass().setAll("primary-button");
        var toolbar = new HBox(8, unlock, lock, reset, space, commit);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        var status = new HBox(12, pending, reload);
        status.setAlignment(Pos.CENTER_LEFT);
        notice.setWrapText(true);
        notice.setId("achievement-notice");
        pending.setId("preview-count");
        empty.setWrapText(true);
        empty.setMaxWidth(420);
        list.setId("achievement-list");
        list.getStyleClass().add("achievement-list");
        list.setFixedCellSize(112);
        list.setPlaceholder(empty);
        list.setCellFactory(view -> new AchievementCell());
        var body = new VBox(14, warning, toolbar, status, notice, list);
        body.getStyleClass().add("achievement-body");
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(header, new Separator(), body);
        unlock.setOnAction(e -> { preview.setAll(true); changed(); });
        lock.setOnAction(e -> { preview.setAll(false); changed(); });
        reset.setOnAction(e -> { preview.reset(); changed(); });
        commit.setOnAction(e -> notice.setText("Preview only: " + preview.changeCount()
                + " change(s). Nothing was sent to Steam. Commit is not implemented yet."));
        reload.setOnAction(e -> load());
        updateControls();
    }

    public void open(Game next, String apiKey, String account) {
        if (next != null && game != null && next.appId() == game.appId()
                && java.util.Objects.equals(key, apiKey) && java.util.Objects.equals(steamId, account)) return;
        cancel();
        game = next;
        key = apiKey;
        steamId = account;
        title.setText(next == null ? "Choose a game" : next.name());
        subtitle.setText(next == null ? "Select a game from the sidebar" : "APP " + next.appId());
        if (next == null) {
            clear();
            empty.setText("Select a game to load its achievements.");
        } else load();
    }

    private void load() {
        cancel();
        clear();
        if (game == null) return;
        empty.setText("Loading achievements from Steam…");
        final long appId = game.appId();
        final String requestKey = key, account = steamId;
        var next = new Task<AchievementList>() {
            @Override protected AchievementList call() throws Exception {
                return api.loadAchievements(requestKey, account, appId);
            }
        };
        task = next;
        updateControls();
        next.setOnSucceeded(e -> {
            if (task != next) return;
            task = null;
            var result = next.getValue();
            preview = new AchievementPreview(result.achievements());
            dataNotice = result.notice();
            list.getItems().setAll(result.achievements());
            subtitle.setText("APP " + appId + "  ·  " + result.achievements().size() + " achievements");
            empty.setText("Steam returned no achievement definitions for this game.");
            changed();
        });
        next.setOnFailed(e -> {
            if (task != next) return;
            task = null;
            empty.setText("Achievements couldn't be loaded. Use Reload to try again.");
            notice.setText(next.getException() instanceof SteamApiException ex ? ex.getMessage()
                    : "Unable to load achievements. Please try again.");
            updateControls();
        });
        requests.submit(next);
    }

    private void clear() {
        preview = new AchievementPreview(java.util.List.of());
        list.getItems().clear();
        icons.clear();
        dataNotice = "";
        changed();
    }

    private void changed() {
        notice.setText(dataNotice);
        pending.setText(preview.changeCount() + " preview changes");
        list.refresh();
        updateControls();
    }

    private void updateControls() {
        boolean editable = task == null && preview.achievements().stream()
                .anyMatch(a -> a.state() != Achievement.State.UNKNOWN);
        unlock.setDisable(!editable);
        lock.setDisable(!editable);
        reset.setDisable(!editable || preview.changeCount() == 0);
        commit.setDisable(!editable || preview.changeCount() == 0);
        reload.setDisable(game == null || task != null);
    }

    private Image icon(Achievement achievement) {
        String url = preview.isUnlocked(achievement) ? achievement.icon() : achievement.lockedIcon();
        if (url.isBlank()) return null;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || !(host.endsWith(".steamstatic.com") || host.endsWith(".steamcommunity.com")
                    || host.equals("steamcdn-a.akamaihd.net"))) return null;
        } catch (IllegalArgumentException ex) { return null; }
        Image image = icons.get(url);
        if (image == null) {
            image = new Image(url, 48, 48, true, true, true);
            icons.put(url, image);
            if (icons.size() > 100) icons.remove(icons.keySet().iterator().next());
        }
        return image;
    }

    private final class AchievementCell extends ListCell<Achievement> {
        private final CheckBox check = new CheckBox();
        private final ImageView image = new ImageView();
        private final Label name = label("", "achievement-name");
        private final Label description = label("", "achievement-description");
        private final Label id = label("", "achievement-id");
        private final Label state = label("", "achievement-state");
        private final HBox row;

        AchievementCell() {
            image.setFitWidth(48);
            image.setFitHeight(48);
            var cover = new StackPane(image);
            cover.setMinSize(48, 48);
            cover.setMaxSize(48, 48);
            cover.getStyleClass().add("achievement-cover");
            var text = new VBox(5, name, description, id);
            text.setMinWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            description.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);
            row = new HBox(14, check, cover, text, state);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("achievement-row");
            check.setOnAction(e -> {
                if (getItem() != null) {
                    preview.setUnlocked(getItem(), check.isSelected());
                    changed();
                }
            });
        }

        @Override protected void updateItem(Achievement achievement, boolean emptyCell) {
            super.updateItem(achievement, emptyCell);
            setText(null);
            row.getStyleClass().removeAll("unlocked-row", "locked-row", "unknown-row", "changed-row");
            if (emptyCell || achievement == null) {
                setGraphic(null);
                image.setImage(null);
                return;
            }
            boolean unknown = achievement.state() == Achievement.State.UNKNOWN;
            boolean unlocked = preview.isUnlocked(achievement);
            boolean edited = preview.isChanged(achievement);
            row.getStyleClass().add(unknown ? "unknown-row" : unlocked ? "unlocked-row" : "locked-row");
            if (edited) row.getStyleClass().add("changed-row");
            check.setSelected(unlocked);
            check.setIndeterminate(unknown);
            check.setDisable(unknown);
            check.setAccessibleText("Preview unlock state: " + achievement.name());
            image.setImage(icon(achievement));
            name.setText(achievement.name());
            description.setText(achievement.description().isBlank() ? "No description provided" : achievement.description());
            description.setTooltip(new Tooltip(achievement.description()));
            id.setText(achievement.id());
            state.setText(unknown ? "UNAVAILABLE" : (edited ? "PREVIEW: " : "") + (unlocked ? "UNLOCKED" : "LOCKED"));
            setGraphic(row);
            row.prefWidthProperty().bind(widthProperty().subtract(4));
        }
    }

    private void cancel() { if (task != null) task.cancel(true); task = null; }
    @Override public void close() {
        cancel();
        key = null;
        steamId = null;
        requests.shutdownNow();
        icons.clear();
    }
    private static Label label(String text, String style) {
        var label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }
    private static Button button(String text, String id) {
        var button = new Button(text);
        button.setId(id);
        button.getStyleClass().add("secondary-button");
        return button;
    }
}
