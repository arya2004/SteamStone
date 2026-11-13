package steamstone.ui.library;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lombok.RequiredArgsConstructor;

import steamstone.exception.StoneException;
import steamstone.model.Game;
import steamstone.model.SteamLibrary;
import steamstone.steam.SteamWebApi;
import steamstone.ui.AppIcon;
import steamstone.ui.achievement.AchievementEditor;
import steamstone.ui.task.StoneTask;

/**
 * Owns this one screen's state. API requests run outside the JavaFX application thread.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class LibraryView extends BorderPane implements AutoCloseable {
    private final SteamWebApi api;
    private final ExecutorService requests = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("steamstone-api").factory());
    private final PasswordField keyField = new PasswordField();
    private final TextField profileField = new TextField();
    private final Button connectButton = new Button("Load my library  →");
    private final Button refreshButton = new Button("↻  Refresh");
    private final Button disconnectButton = new Button("Disconnect");
    private final Label connectionError = new Label();
    private final Label connectionStatus = new Label();
    private final Label libraryStatus = new Label();
    private final Label profileName = new Label();
    private final Label gameCount = new Label("0");
    private final AchievementEditor editor;
    private final Provider<GameCell> cells;
    private final Label sidebarEmptyMessage = new Label("Connect your library to see your games.");
    private final TextField search = new TextField();
    private final ListView<Game> gameList = new ListView<>();
    private final Map<String, Image> icons = new LinkedHashMap<>(32, .75f, true);
    private final StackPane screens = new StackPane();
    private final VBox connectionScreen = connectionScreen();
    private SteamLibrary library;
    private Game selectedGame;
    private Task<SteamLibrary> currentTask;
    private String sessionKey;
    private String sessionProfile;

    @Inject
    private void initialize() {
        getStyleClass().add("app-root");
        setLeft(sidebar());
        getLeft().disableProperty().bind(editor.committingProperty());
        screens.getChildren().addAll(connectionScreen, editor);
        showLibrary(false);

        final var workspace = new VBox(0, screens);
        VBox.setVgrow(screens, Priority.ALWAYS);
        setCenter(workspace);

        keyField.setId("api-key");
        profileField.setId("steam-profile");
        connectButton.setId("load-library");
        gameList.setId("game-list");
        gameCount.setId("game-count");
        connectButton.setOnAction((final var event) -> connect());
        profileField.setOnAction((final var event) -> connect());
        keyField.setOnAction((final var event) -> profileField.requestFocus());
        refreshButton.setOnAction((final var event) -> load(sessionKey, sessionProfile));
        disconnectButton.setOnAction((final var event) -> disconnect());
        search.textProperty().addListener((final var obs, final var before, final var after) -> filterGames());
        gameList.getSelectionModel().selectedItemProperty()
                .addListener((final var obs, final var before, final var game) -> {
                    if (game != null) {
                        if (!editor.isCurrentGame(game) && !editor.discardPendingIfNeeded()) {
                            gameList.getSelectionModel().select(before);
                            return;
                        }
                        selectGame(game);
                    }
                });
    }

    private Node sidebar() {
        final var stone = new ImageView(AppIcon.load(40));
        stone.setFitWidth(40);
        stone.setFitHeight(40);
        stone.setPreserveRatio(true);
        stone.setAccessibleText("SteamStone logo");
        final var brand = new HBox(12, stone, new VBox(4,
                label("STEAMSTONE", "brand-name"), label("Your achievement workspace.", "brand-caption")));
        brand.setAlignment(Pos.CENTER_LEFT);

        gameCount.getStyleClass().add("game-count-badge");
        final var grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        final var heading = new HBox(10, label("ALL GAMES", "eyebrow"), grow, gameCount);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(heading, new Insets(30, 0, 0, 0));
        search.setPromptText("Search games…");
        search.setId("search-games");
        search.setPrefHeight(38);
        search.setDisable(true);

        gameList.getStyleClass().add("sidebar-game-list");
        gameList.setFixedCellSize(52);
        gameList.setCellFactory((final var view) -> cells.get());
        sidebarEmptyMessage.getStyleClass().add("sidebar-empty");
        sidebarEmptyMessage.setWrapText(true);
        sidebarEmptyMessage.setMaxWidth(230);
        gameList.setPlaceholder(sidebarEmptyMessage);
        VBox.setVgrow(gameList, Priority.ALWAYS);

        profileName.setText("Not connected");
        profileName.getStyleClass().add("sidebar-account-name");
        profileName.setMaxWidth(Double.MAX_VALUE);
        profileName.setTextOverrun(OverrunStyle.ELLIPSIS);
        refreshButton.setText("Refresh");
        refreshButton.setId("refresh-library");
        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setDisable(true);
        disconnectButton.getStyleClass().add("text-button");
        disconnectButton.setDisable(true);
        final var actions = new HBox(8, refreshButton, disconnectButton);
        libraryStatus.getStyleClass().add("library-status");
        libraryStatus.setWrapText(true);
        libraryStatus.setId("library-status");
        hide(libraryStatus);
        final var bottom =
                new VBox(10, new Separator(), label("ACCOUNT", "eyebrow"), profileName, actions, libraryStatus);
        final var box = new VBox(14, brand, heading, search, gameList, bottom);
        box.setPrefWidth(300);
        box.setMinWidth(300);
        box.setMaxWidth(300);
        box.getStyleClass().add("sidebar");
        return box;
    }

    private VBox connectionScreen() {
        final var eyebrow = label("YOUR GAMES. YOUR SPACE.", "accent-eyebrow");
        final var title = label("Your Steam library,\nin one place.", "welcome-title");
        final var subtitle =
                label("Bring your collection into SteamStone. Connect once, then browse\nevery game Steam makes " +
                                "available to your account.",
                        "welcome-subtitle");
        subtitle.setWrapText(true);
        final var intro = new VBox(14, eyebrow, title, subtitle);

        final var formTitle = label("Connect your library", "card-title");
        final var formSubtitle = label("Use your personal Steam Web API key and profile.", "muted");
        keyField.setPromptText("Paste your 32-character API key");
        keyField.setAccessibleText("Steam Web API key");
        keyField.setPrefHeight(44);
        profileField.setPromptText("steamcommunity.com/id/your-name or SteamID64");
        profileField.setAccessibleText("Steam profile URL or SteamID64");
        profileField.setPrefHeight(44);
        final var keyLabel = label("STEAM WEB API KEY", "field-label");
        keyLabel.setLabelFor(keyField);
        final var profileLabel = label("STEAM PROFILE", "field-label");
        profileLabel.setLabelFor(profileField);
        final var keyInput = new VBox(8, keyLabel, keyField);
        final var profileInput = new VBox(8, profileLabel, profileField);
        final var inputHint =
                label("The key authorizes access; your profile tells us which library to load.", "field-hint");
        inputHint.setWrapText(true);
        connectButton.getStyleClass().add("primary-button");
        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setPrefHeight(46);
        connectionStatus.getStyleClass().add("muted");
        connectionError.getStyleClass().add("error-message");
        connectionError.setWrapText(true);
        hide(connectionError);
        hide(connectionStatus);
        final var card = new VBox(14, new VBox(5, formTitle, formSubtitle), keyInput, profileInput,
                inputHint, connectButton, connectionStatus, connectionError);
        card.getStyleClass().add("connection-card");
        VBox.setMargin(keyInput, new Insets(7, 0, 0, 0));
        final var privacy =
                label("◇  Session only. Your key stays in memory and is sent only to Steam.", "privacy-note");
        privacy.setWrapText(true);
        final var footer = label("No game installs needed  ·  Read-only library access", "connect-footer");
        final var body = new VBox(22, intro, card, privacy, footer);
        body.setMaxWidth(630);
        body.setAlignment(Pos.TOP_LEFT);
        final var page = new VBox(body);
        page.setAlignment(Pos.TOP_CENTER);
        page.getStyleClass().add("connection-page");
        return page;
    }

    private void selectGame(final Game game) {
        selectedGame = game;
        editor.open(game, sessionKey, library == null ? null : library.steamId());
    }

    private void connect() {
        if (currentTask != null)
            return;
        load(keyField.getText(), profileField.getText());
    }

    private void load(final String key, final String profile) {
        if (currentTask != null)
            return;
        final var nextKey = key == null ? "" : key.strip();
        final var nextProfile = profile == null ? "" : profile.strip();
        hide(connectionError);
        connectionStatus.setText("Connecting to Steam and loading your library…");
        show(connectionStatus);
        setBusy(true);
        if (library != null)
            libraryStatus.setText("Refreshing from Steam… Your current list is still visible.");

        final var task = new StoneTask<>(() -> api.loadLibrary(nextKey, nextProfile));
        currentTask = task;
        task.setOnSucceeded((final var event) -> {
            if (currentTask != task)
                return;
            currentTask = null;
            setBusy(false);
            library = task.getValue();
            refreshButton.setDisable(false);
            sessionKey = nextKey;
            sessionProfile = nextProfile;
            keyField.clear();
            profileName.setText(library.playerName());
            gameCount.setText(String.format(Locale.ROOT, "%,d", library.games().size()));
            search.setDisable(false);
            disconnectButton.setDisable(false);
            filterGames();
            showLibrary(true);
            show(libraryStatus);
            final var time =
                    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(library.fetchedAt());
            libraryStatus.setText(library.games().isEmpty()
                    ?
                    "Steam returned no games. This can reflect library visibility; it is not proof that the account " +
                            "owns none."
                    : "Updated at " + time);
        });
        task.setOnFailed((final var event) -> {
            if (currentTask != task)
                return;
            currentTask = null;
            setBusy(false);
            hide(connectionStatus);
            final var message = StoneException.codeOf(task.getException()).message();
            if (library == null) {
                connectionError.setText(message);
                show(connectionError);
            } else {
                libraryStatus.setText(message + " Showing the earlier session snapshot.");
            }
        });
        requests.submit(task);
    }

    private void filterGames() {
        if (library == null)
            return;
        final var query = search.getText().strip().toLowerCase(Locale.ROOT);
        final var visible = library.games().stream()
                .filter((final var game) -> game.name().toLowerCase(Locale.ROOT).contains(query)
                        || Long.toString(game.appId()).contains(query))
                .sorted(Comparator.comparing(Game::name, String.CASE_INSENSITIVE_ORDER).thenComparingLong(Game::appId))
                .toList();
        gameList.getSelectionModel().clearSelection();
        gameList.getItems().setAll(visible);
        if (selectedGame != null) {
            final var id = selectedGame.appId();
            selectGame(
                    library.games().stream().filter((final var game) -> game.appId() == id).findFirst().orElse(null));
        }
        if (selectedGame != null && visible.contains(selectedGame)) {
            gameList.getSelectionModel().select(selectedGame);
        } else if (selectedGame == null && !visible.isEmpty()) {
            gameList.getSelectionModel().selectFirst();
        }
        sidebarEmptyMessage.setText(library.games().isEmpty() ? "Steam returned no games." : "No matching games.");
    }

    private void disconnect() {
        if (!editor.discardPendingIfNeeded())
            return;
        if (currentTask != null)
            currentTask.cancel(true);
        currentTask = null;
        sessionKey = null;
        sessionProfile = null;
        library = null;
        keyField.clear();
        profileField.clear();
        gameList.getItems().clear();
        icons.clear();
        search.clear();
        hide(connectionError);
        hide(connectionStatus);
        setBusy(false);
        profileName.setText("Not connected");
        gameCount.setText("0");
        search.setDisable(true);
        refreshButton.setDisable(true);
        disconnectButton.setDisable(true);
        hide(libraryStatus);
        sidebarEmptyMessage.setText("Connect your library to see your games.");
        selectGame(null);
        showLibrary(false);
        keyField.requestFocus();
    }

    private void setBusy(final boolean value) {
        connectButton.setDisable(value);
        connectButton.setText(value ? "Loading your library…" : "Load my library  →");
        refreshButton.setDisable(value || library == null);
        keyField.setDisable(value);
        profileField.setDisable(value);
    }

    private void showLibrary(final boolean value) {
        connectionScreen.setVisible(!value);
        connectionScreen.setManaged(!value);
        editor.setVisible(value);
        editor.setManaged(value);
    }

    public Image iconFor(final Game game) {
        final var url = game.iconUrl();
        if (url == null)
            return null;
        final var cached = icons.get(url);
        if (cached != null)
            return cached;
        final var image = new Image(url, 28, 28, true, true, true);
        icons.put(url, image);
        if (icons.size() > 160)
            icons.remove(icons.keySet().iterator().next());
        return image;
    }

    public static Label label(final String text, final String styleClass) {
        final var label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static void hide(final Node node) {
        node.setVisible(false);
        node.setManaged(false);
    }

    private static void show(final Node node) {
        node.setVisible(true);
        node.setManaged(true);
    }

    public boolean canClose() {
        return editor.discardPendingIfNeeded();
    }

    @Override
    public void close() {
        if (currentTask != null)
            currentTask.cancel(true);
        sessionKey = null;
        keyField.clear();
        icons.clear();
        requests.shutdownNow();
        editor.close();
    }
}
