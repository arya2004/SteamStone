package steamstone.ui;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.stage.Stage;
import javafx.util.Duration;
import steamstone.model.Game;
import steamstone.steam.SteamWebApi;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Optional graphical/read-only integration check, excluded from the shipped application. */
public final class UiSmokeCheck {
    public static void main(String[] args) { Application.launch(CheckApplication.class, args); }

    public static final class CheckApplication extends Application {
        private LibraryView view;
        private Timeline poll;
        private int ticks;
        private boolean selectedGame;

        @Override public void start(Stage stage) throws Exception {
            Path output = Path.of("target", "verification");
            Files.createDirectories(output);
            view = new LibraryView(new SteamWebApi());
            Scene scene = new Scene(view, 1180, 800);
            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            stage.setTitle("SteamStone — UI verification");
            stage.setScene(scene);
            stage.show();
            PauseTransition initial = new PauseTransition(Duration.seconds(1));
            initial.setOnFinished(event -> {
                try {
                    snapshot(output.resolve("connect.png"));
                    String keyFile = System.getenv("STEAMSTONE_TEST_KEY_FILE");
                    String profile = System.getenv("STEAMSTONE_TEST_PROFILE");
                    if (keyFile == null || profile == null) {
                        System.out.println("Connection screen rendered; no live credentials supplied.");
                        view.close();
                        Platform.exit();
                        return;
                    }
                    String contents = Files.readString(Path.of(keyFile));
                    var matcher = Pattern.compile("(?<![a-fA-F0-9])[a-fA-F0-9]{32}(?![a-fA-F0-9])").matcher(contents);
                    if (!matcher.find()) throw new IllegalArgumentException("No key found in the supplied test file.");
                    ((PasswordField) view.lookup("#api-key")).setText(matcher.group());
                    ((TextField) view.lookup("#steam-profile")).setText(profile);
                    ((Button) view.lookup("#load-library")).fire();
                    poll = new Timeline(new KeyFrame(Duration.seconds(1), e -> checkLoaded(output)));
                    poll.setCycleCount(Timeline.INDEFINITE);
                    poll.play();
                } catch (Exception ex) { fail(); }
            });
            initial.play();
        }

        @SuppressWarnings("unchecked")
        private void checkLoaded(Path output) {
            if (++ticks > 55) { fail(); return; }
            var count = (Label) view.lookup("#game-count");
            if (count == null || count.getText().equals("0")) return;
            try {
                var list = (ListView<Game>) view.lookup("#game-list");
                if (list.getItems().isEmpty()) throw new AssertionError("No UI rows loaded");
                if (!((PasswordField) view.lookup("#api-key")).getText().isEmpty()) throw new AssertionError("Key field not cleared");
                var selected = list.getItems().get(1);
                if (!selectedGame) {
                    list.getSelectionModel().select(selected);
                    selectedGame = true;
                    return;
                }
                var achievements = (ListView<?>) view.lookup("#achievement-list");
                if (achievements.getItems().isEmpty()) return;
                poll.stop();
                if (!((Label) view.lookup("#editor-game-name")).getText().equals(selected.name())) {
                    throw new AssertionError("Sidebar selection did not update editor context");
                }
                if (list.localToScene(list.getBoundsInLocal()).getMinX() > 300) {
                    throw new AssertionError("Game list is not in the sidebar");
                }
                snapshot(output.resolve("library.png"));
                var unlockAll = (Button) view.lookup("#unlock-all");
                var lockAll = (Button) view.lookup("#lock-all");
                unlockAll.fire();
                lockAll.fire();
                var commit = (Button) view.lookup("#commit-preview");
                if (commit.isDisabled()) {
                    unlockAll.fire();
                }
                if (commit.isDisabled()) throw new AssertionError("No local preview changes produced");
                commit.fire();
                if (!((Label) view.lookup("#achievement-notice")).getText().contains("Nothing was sent to Steam")) {
                    throw new AssertionError("Commit preview message missing");
                }
                ((Button) view.lookup("#reset-preview")).fire();
                if (!((Label) view.lookup("#preview-count")).getText().startsWith("0 ")) {
                    throw new AssertionError("Reset did not clear preview");
                }
                var checkbox = (javafx.scene.control.CheckBox) achievements.lookup(".check-box");
                if (checkbox == null || checkbox.isDisabled()) throw new AssertionError("No editable visible checkbox");
                checkbox.fire();
                if (!((Label) view.lookup("#preview-count")).getText().startsWith("1 ")) {
                    throw new AssertionError("Checkbox did not stage a preview");
                }
                ((Button) view.lookup("#reset-preview")).fire();
                int total = list.getItems().size();
                var search = (TextField) view.lookup("#search-games");
                search.setText(Long.toString(list.getItems().getFirst().appId()));
                if (list.getItems().isEmpty() || list.getItems().size() > total) throw new AssertionError("Search failed");
                search.clear();
                if (list.getItems().size() != total) throw new AssertionError("Search clear did not restore rows");
                System.out.println("Graphical live check passed: " + total + " games; " + achievements.getItems().size() + " achievements; checkbox, bulk preview, dummy Commit, reset, search and credential-field clearing verified.");
                view.close();
                Platform.exit();
            } catch (Throwable ex) { fail(); }
        }

        private void snapshot(Path path) throws Exception {
            view.applyCss();
            view.layout();
            var snapshot = view.snapshot(null, null);
            int width = (int) snapshot.getWidth();
            int height = (int) snapshot.getHeight();
            int[] pixels = new int[width * height];
            snapshot.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, pixels, 0, width);
            ImageIO.write(image, "png", path.toFile());
        }

        private void fail() {
            System.err.println("UI verification failed. No credential values or remote response bodies were logged.");
            if (poll != null) poll.stop();
            if (view != null) view.close();
            Platform.exit();
            System.exit(1);
        }

        @Override public void stop() { if (view != null) view.close(); }
    }
}
