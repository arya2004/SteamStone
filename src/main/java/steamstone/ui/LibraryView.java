package steamstone.ui;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Polygon;
import steamstone.model.Game;
import steamstone.model.SteamLibrary;
import steamstone.steam.SteamApiException;
import steamstone.steam.SteamWebApi;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns this one screen's state. API requests run outside the JavaFX application thread. */
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
    private final Label hoursCount = new Label("0");
    private final Label playedCount = new Label("0");
    private final Label listCount = new Label();
    private final Label accountBadge = new Label("NOT CONNECTED");
    private final Label sidebarName = new Label("Your next great game\nis already in your library.");
    private final TextField search = new TextField();
    private final ComboBox<String> sort = new ComboBox<>(FXCollections.observableArrayList(
            "Name: A–Z", "Most played", "Recently played"));
    private final ListView<Game> gameList = new ListView<>();
    private final Map<String, Image> icons = new LinkedHashMap<>(32, .75f, true);
    private final StackPane screens = new StackPane();
    private final VBox connectionScreen;
    private final VBox libraryScreen;
    private SteamLibrary library;
    private Task<SteamLibrary> currentTask;
    private String sessionKey;
    private String sessionProfile;

    public LibraryView(SteamWebApi api) {
        this.api = api;
        getStyleClass().add("app-root");
        setLeft(sidebar());
        connectionScreen = connectionScreen();
        libraryScreen = libraryScreen();
        screens.getChildren().addAll(connectionScreen, libraryScreen);
        showLibrary(false);

        var workspace = new VBox(0, masthead(), screens);
        VBox.setVgrow(screens, Priority.ALWAYS);
        setCenter(workspace);

        keyField.setId("api-key");
        profileField.setId("steam-profile");
        connectButton.setId("load-library");
        gameList.setId("game-list");
        gameCount.setId("game-count");
        connectButton.setOnAction(event -> connect());
        profileField.setOnAction(event -> connect());
        keyField.setOnAction(event -> profileField.requestFocus());
        refreshButton.setOnAction(event -> load(sessionKey, sessionProfile));
        disconnectButton.setOnAction(event -> disconnect());
        search.textProperty().addListener((obs, before, after) -> filterGames());
        sort.valueProperty().addListener((obs, before, after) -> filterGames());
    }

    private Node sidebar() {
        var stone = new Polygon(15, 0, 30, 10, 26, 30, 10, 34, 0, 20, 3, 6);
        stone.getStyleClass().add("stone-logo");
        var brandName = label("STEAMSTONE", "brand-name");
        var brandCaption = label("A home for your games.", "brand-caption");
        var brand = new HBox(12, stone, new VBox(4, brandName, brandCaption));
        brand.setAlignment(Pos.CENTER_LEFT);

        var libraryNav = new HBox(12, label("▦", "nav-symbol"), label("Library", "nav-text"));
        libraryNav.setAlignment(Pos.CENTER_LEFT);
        libraryNav.getStyleClass().add("nav-active");
        var section = label("WORKSPACE", "eyebrow");
        VBox.setMargin(section, new Insets(46, 0, 12, 10));
        var spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebarName.getStyleClass().add("sidebar-copy");
        sidebarName.setWrapText(true);
        var bottom = new VBox(16, new Separator(), sidebarName,
                label("STEAMSTONE  /  0.1", "sidebar-version"));
        var box = new VBox(0, brand, section, libraryNav, spacer, bottom);
        box.setPrefWidth(224);
        box.getStyleClass().add("sidebar");
        return box;
    }

    private Node masthead() {
        var grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        accountBadge.getStyleClass().add("account-badge");
        var bar = new HBox(10, label("WORKSPACE", "masthead-muted"), label("/", "masthead-muted"),
                label("Library", "masthead-current"), grow, accountBadge);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("masthead");
        return bar;
    }

    private VBox connectionScreen() {
        var eyebrow = label("YOUR GAMES. YOUR SPACE.", "accent-eyebrow");
        var title = label("Your Steam library,\nin one place.", "welcome-title");
        var subtitle = label("Bring your collection into SteamStone. Connect once, then browse\nevery game Steam makes available to your account.", "welcome-subtitle");
        subtitle.setWrapText(true);
        var intro = new VBox(14, eyebrow, title, subtitle);

        var formTitle = label("Connect your library", "card-title");
        var formSubtitle = label("Use your personal Steam Web API key and profile.", "muted");
        keyField.setPromptText("Paste your 32-character API key");
        keyField.setAccessibleText("Steam Web API key");
        keyField.setPrefHeight(44);
        profileField.setPromptText("steamcommunity.com/id/your-name or SteamID64");
        profileField.setAccessibleText("Steam profile URL or SteamID64");
        profileField.setPrefHeight(44);
        var keyLabel = label("STEAM WEB API KEY", "field-label");
        keyLabel.setLabelFor(keyField);
        var profileLabel = label("STEAM PROFILE", "field-label");
        profileLabel.setLabelFor(profileField);
        var keyInput = new VBox(8, keyLabel, keyField);
        var profileInput = new VBox(8, profileLabel, profileField);
        var inputHint = label("The key authorizes access; your profile tells us which library to load.", "field-hint");
        inputHint.setWrapText(true);
        connectButton.getStyleClass().add("primary-button");
        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setPrefHeight(46);
        connectionStatus.getStyleClass().add("muted");
        connectionError.getStyleClass().add("error-message");
        connectionError.setWrapText(true);
        hide(connectionError);
        hide(connectionStatus);
        var card = new VBox(14, new VBox(5, formTitle, formSubtitle), keyInput, profileInput,
                inputHint, connectButton, connectionStatus, connectionError);
        card.getStyleClass().add("connection-card");
        VBox.setMargin(keyInput, new Insets(7, 0, 0, 0));
        var privacy = label("◇  Session only. Your key stays in memory and is sent only to Steam.", "privacy-note");
        privacy.setWrapText(true);
        var footer = label("No game installs needed  ·  Read-only library access", "connect-footer");
        var body = new VBox(22, intro, card, privacy, footer);
        body.setMaxWidth(630);
        body.setAlignment(Pos.TOP_LEFT);
        var page = new VBox(body);
        page.setAlignment(Pos.TOP_CENTER);
        page.getStyleClass().add("connection-page");
        return page;
    }

    private VBox libraryScreen() {
        var heading = new VBox(8, label("THE COLLECTION", "accent-eyebrow"), label("Your library", "library-title"));
        profileName.getStyleClass().add("library-subtitle");
        heading.getChildren().add(profileName);
        var grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setId("refresh-library");
        disconnectButton.getStyleClass().add("text-button");
        var actions = new HBox(10, disconnectButton, refreshButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        var top = new HBox(20, heading, grow, actions);
        top.setAlignment(Pos.CENTER_LEFT);

        var stats = new HBox(12, statCard(gameCount, "GAMES IN LIBRARY", "Every title returned by Steam"),
                statCard(hoursCount, "HOURS PLAYED", "A collection of good times"),
                statCard(playedCount, "GAMES PLAYED", "At least one minute recorded"));
        search.setPromptText("Search your games…");
        search.setId("search-games");
        search.setPrefHeight(42);
        HBox.setHgrow(search, Priority.ALWAYS);
        sort.getSelectionModel().selectFirst();
        sort.setPrefWidth(180);
        sort.setPrefHeight(42);
        var tools = new HBox(12, search, sort);
        listCount.getStyleClass().add("eyebrow");
        var readOnly = label("READ-ONLY", "read-only-tag");
        var lineGrow = new Region();
        HBox.setHgrow(lineGrow, Priority.ALWAYS);
        var listHeader = new HBox(listCount, lineGrow, readOnly);
        listHeader.setAlignment(Pos.CENTER_LEFT);
        gameList.setFixedCellSize(78);
        gameList.setCellFactory(view -> new GameCell());
        var empty = new VBox(10, label("Nothing to show yet", "empty-title"),
                label("Try another search. If Steam returned no games, check your Game details privacy.", "muted"));
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(20));
        ((Label) empty.getChildren().get(1)).setWrapText(true);
        gameList.setPlaceholder(empty);
        VBox.setVgrow(gameList, Priority.ALWAYS);
        libraryStatus.getStyleClass().add("library-status");
        libraryStatus.setWrapText(true);
        libraryStatus.setId("library-status");
        var page = new VBox(20, top, stats, tools, listHeader, gameList, libraryStatus);
        page.getStyleClass().add("library-page");
        return page;
    }

    private Node statCard(Label number, String caption, String description) {
        number.getStyleClass().add("stat-number");
        var box = new VBox(7, label(caption, "stat-caption"), number, label(description, "stat-description"));
        box.getStyleClass().add("stat-card");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void connect() {
        if (currentTask != null) return;
        load(keyField.getText(), profileField.getText());
    }

    private void load(String key, String profile) {
        if (currentTask != null) return;
        String nextKey = key == null ? "" : key.strip();
        String nextProfile = profile == null ? "" : profile.strip();
        hide(connectionError);
        connectionStatus.setText("Connecting to Steam and loading your library…");
        show(connectionStatus);
        setBusy(true);
        if (library != null) libraryStatus.setText("Refreshing from Steam… Your current list is still visible.");

        var task = new Task<SteamLibrary>() {
            @Override protected SteamLibrary call() throws Exception { return api.loadLibrary(nextKey, nextProfile); }
        };
        currentTask = task;
        task.setOnSucceeded(event -> {
            if (currentTask != task) return;
            currentTask = null;
            setBusy(false);
            library = task.getValue();
            sessionKey = nextKey;
            sessionProfile = nextProfile;
            keyField.clear();
            profileName.setText(library.playerName() + "  ·  " + library.steamId());
            gameCount.setText(String.format(Locale.ROOT, "%,d", library.games().size()));
            hoursCount.setText(String.format(Locale.ROOT, "%,.0f", library.playtimeMinutes() / 60.0));
            playedCount.setText(String.format(Locale.ROOT, "%,d", library.games().stream().filter(g -> g.playtimeMinutes() > 0).count()));
            accountBadge.setText("●  CONNECTED TO STEAM");
            if (!accountBadge.getStyleClass().contains("connected")) accountBadge.getStyleClass().add("connected");
            sidebarName.setText("Connected as\n" + library.playerName());
            filterGames();
            showLibrary(true);
            String time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(library.fetchedAt());
            libraryStatus.setText(library.games().isEmpty()
                    ? "Steam returned no games. This can reflect library visibility; it is not proof that the account owns none."
                    : "Updated at " + time + "  ·  Includes uninstalled games  ·  Results depend on Steam's library visibility");
        });
        task.setOnFailed(event -> {
            if (currentTask != task) return;
            currentTask = null;
            setBusy(false);
            hide(connectionStatus);
            String message = task.getException() instanceof SteamApiException ex ? ex.getMessage()
                    : "The library couldn't be loaded. Please try again.";
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
        if (library == null) return;
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        Comparator<Game> order = switch (sort.getSelectionModel().getSelectedIndex()) {
            case 1 -> Comparator.comparingLong(Game::playtimeMinutes).reversed();
            case 2 -> Comparator.comparingLong(Game::lastPlayed).reversed();
            default -> Comparator.comparing(Game::name, String.CASE_INSENSITIVE_ORDER);
        };
        var visible = library.games().stream()
                .filter(game -> game.name().toLowerCase(Locale.ROOT).contains(query) || Long.toString(game.appId()).contains(query))
                .sorted(order.thenComparing(Game::name, String.CASE_INSENSITIVE_ORDER).thenComparingLong(Game::appId))
                .toList();
        gameList.getItems().setAll(visible);
        listCount.setText(String.format(Locale.ROOT, "%,d OF %,d GAMES", visible.size(), library.games().size()));
    }

    private void disconnect() {
        if (currentTask != null) currentTask.cancel(true);
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
        accountBadge.setText("NOT CONNECTED");
        accountBadge.getStyleClass().removeIf("connected"::equals);
        sidebarName.setText("Your next great game\nis already in your library.");
        showLibrary(false);
        keyField.requestFocus();
    }

    private void setBusy(boolean value) {
        connectButton.setDisable(value);
        connectButton.setText(value ? "Loading your library…" : "Load my library  →");
        refreshButton.setDisable(value);
        keyField.setDisable(value);
        profileField.setDisable(value);
    }

    private void showLibrary(boolean value) {
        connectionScreen.setVisible(!value);
        connectionScreen.setManaged(!value);
        libraryScreen.setVisible(value);
        libraryScreen.setManaged(value);
    }

    private Image iconFor(Game game) {
        String url = game.iconUrl();
        if (url == null) return null;
        Image cached = icons.get(url);
        if (cached != null) return cached;
        var image = new Image(url, 48, 48, true, true, true);
        icons.put(url, image);
        if (icons.size() > 160) icons.remove(icons.keySet().iterator().next());
        return image;
    }

    private final class GameCell extends ListCell<Game> {
        private final Label initial = label("", "game-initial");
        private final ImageView image = new ImageView();
        private final Label name = label("", "game-name");
        private final Label appId = label("", "game-id");
        private final Label time = label("", "game-time");
        private final HBox row;

        GameCell() {
            image.setFitWidth(48);
            image.setFitHeight(48);
            var cover = new StackPane(initial, image);
            cover.getStyleClass().add("game-cover");
            cover.setMinSize(48, 48);
            cover.setMaxSize(48, 48);
            var text = new VBox(5, name, appId);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);
            time.setMinWidth(Region.USE_PREF_SIZE);
            row = new HBox(16, cover, text, time);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("game-row");
            name.setMaxWidth(Double.MAX_VALUE);
            name.setTextOverrun(OverrunStyle.ELLIPSIS);
        }

        @Override protected void updateItem(Game game, boolean empty) {
            super.updateItem(game, empty);
            if (empty || game == null) {
                image.setImage(null);
                setGraphic(null);
                setText(null);
                return;
            }
            initial.setText(game.name().substring(0, 1).toUpperCase(Locale.ROOT));
            image.setImage(iconFor(game));
            name.setText(game.name());
            appId.setText("APP " + game.appId());
            time.setText(game.playtimeMinutes() == 0 ? "Not played" :
                    String.format(Locale.ROOT, "%,.1f h played", game.playtimeMinutes() / 60.0));
            setGraphic(row);
            setText(null);
        }
    }

    private static Label label(String text, String styleClass) {
        var label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static void hide(Node node) { node.setVisible(false); node.setManaged(false); }
    private static void show(Node node) { node.setVisible(true); node.setManaged(true); }

    @Override public void close() {
        if (currentTask != null) currentTask.cancel(true);
        sessionKey = null;
        keyField.clear();
        icons.clear();
        requests.shutdownNow();
    }
}
