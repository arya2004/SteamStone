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
    private final Label empty = label("Select a game to load its achievements.", "editor-empty-description");
    private final ListView<Achievement> list = new ListView<>();
    private final Button unlock = button("Unlock all", "unlock-all");
    private final Button lock = button("Lock all", "lock-all");
    private final Button reset = button("Reset", "reset-preview");
    private final Button commit = button("Commit", "commit-preview");
    private final Button reload = button("↻", "reload-achievements");
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
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinWidth(0);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        var heading = new VBox(2, title, subtitle);
        heading.setMinWidth(0);
        HBox.setHgrow(heading, Priority.ALWAYS);
        commit.getStyleClass().setAll("primary-button");
        reload.setTooltip(new Tooltip("Reload achievements"));
        reset.setTooltip(new Tooltip("Reset local changes"));
        var header = new HBox(7, heading, reload, unlock, lock, reset, commit);
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
        list.setCellFactory(view -> new AchievementCell());
        var body = new VBox(6, notice, list);
        body.getStyleClass().add("achievement-body");
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(header, new Separator(), body);
        unlock.setOnAction(e -> { preview.setAll(true); changed(); });
        lock.setOnAction(e -> { preview.setAll(false); changed(); });
        reset.setOnAction(e -> { preview.reset(); changed(); });
        commit.setOnAction(e -> notice.setText("Commit is not implemented yet. Nothing was sent to Steam."));
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
        title.setTooltip(next == null ? null : new Tooltip(next.name()));
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
            image = new Image(url, 32, 32, true, true, true);
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
        private final Label state = label("", "achievement-state");
        private final HBox row;

        AchievementCell() {
            image.setFitWidth(32);
            image.setFitHeight(32);
            var cover = new StackPane(image);
            cover.setMinSize(32, 32);
            cover.setMaxSize(32, 32);
            cover.getStyleClass().add("achievement-cover");
            var text = new VBox(3, name, description);
            text.setMinWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            description.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);
            row = new HBox(10, check, cover, text, state);
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
            name.setTooltip(new Tooltip(achievement.name() + "\n" + achievement.id()));
            state.setText(unknown ? "UNAVAILABLE" : (edited ? "• " : "") + (unlocked ? "UNLOCKED" : "LOCKED"));
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
