package steamstone.ui.verification;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;
import steamstone.inject.SteamStoneInjector;
import com.google.inject.util.Modules;
import steamstone.inject.SteamStoneModule;
import steamstone.inject.SavedConnectionFixtureModule;
import steamstone.settings.ConnectionStore;

import java.util.UUID;

import steamstone.model.Game;
import steamstone.model.achievement.Achievement;
import steamstone.ui.AppIcon;
import steamstone.ui.library.LibraryView;

public final class CheckApplication extends Application {
    private LibraryView view;
    private Timeline poll;
    private int ticks;
    private boolean selectedGame;

    @Override
    public void start(final Stage stage) {
        try {
            final var output = Path.of("target", "verification");
            Files.createDirectories(output);
            view = SteamStoneInjector.create(Modules.override(new SteamStoneModule()).with(
                            new SavedConnectionFixtureModule(new ConnectionStore("steamstone.verify." + UUID.randomUUID()))))
                    .getInstance(LibraryView.class);
            final var scene = new Scene(view, 1180, 800);
            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            stage.setTitle("SteamStone — UI verification");
            AppIcon.apply(stage);
            if (stage.getIcons().isEmpty() || stage.getIcons().stream().anyMatch((final var icon) -> icon.isError())) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
            stage.setScene(scene);
            stage.show();
            final var initial = new PauseTransition(Duration.seconds(1));
            initial.setOnFinished((final var event) -> {
                try {
                    snapshot(output.resolve("connect.png"));
                    final var keyFile = System.getenv("STEAMSTONE_TEST_KEY_FILE");
                    final var profile = System.getenv("STEAMSTONE_TEST_PROFILE");
                    if (keyFile == null || profile == null) {
                        System.out.println("Connection screen rendered; no live credentials supplied.");
                        view.close();
                        Platform.exit();
                        return;
                    }
                    final var contents = Files.readString(Path.of(keyFile));
                    final var matcher =
                            Pattern.compile("(?<![a-fA-F0-9])[a-fA-F0-9]{32}(?![a-fA-F0-9])").matcher(contents);
                    if (!matcher.find())
                        throw new StoneException(ErrorCode.APPLICATION);
                    ((PasswordField) view.lookup("#api-key")).setText(matcher.group());
                    ((TextField) view.lookup("#steam-profile")).setText(profile);
                    ((Button) view.lookup("#load-library")).fire();
                    poll = new Timeline(new KeyFrame(Duration.seconds(1), (final var e) -> checkLoaded(output)));
                    poll.setCycleCount(Timeline.INDEFINITE);
                    poll.play();
                } catch (final Exception e) {
                    fail();
                    throw new StoneException(ErrorCode.APPLICATION, e);
                }
            });
            initial.play();

        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void checkLoaded(final Path output) {
        if (++ticks > 90) {
            fail();
            return;
        }
        final var count = (Label) view.lookup("#game-count");
        if (count == null || count.getText().equals("0"))
            return;
        try {
            final var list = (ListView<Game>) view.lookup("#game-list");
            if (list.getItems().isEmpty())
                throw new StoneException(ErrorCode.APPLICATION);
            if (!((PasswordField) view.lookup("#api-key")).getText().isEmpty())
                throw new StoneException(ErrorCode.APPLICATION);
            final var selected = list.getItems().get(1);
            if (!selectedGame) {
                list.getSelectionModel().select(selected);
                selectedGame = true;
                return;
            }
            final var achievements = (ListView<?>) view.lookup("#achievement-list");
            if (achievements.getItems().isEmpty())
                return;
            poll.stop();
            if (!((Label) view.lookup("#editor-game-name")).getText().equals(selected.name())) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
            if (list.localToScene(list.getBoundsInLocal()).getMinX() > 300) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
            view.applyCss();
            view.layout();
            if (achievements.localToScene(achievements.getBoundsInLocal()).getMinY() > 90) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
            if (achievements.getFixedCellSize() > 60)
                throw new StoneException(ErrorCode.APPLICATION);
            snapshot(output.resolve("library.png"));
            final var unlockAll = (Button) view.lookup("#unlock-all");
            if (unlockAll.isDisabled())
                throw new StoneException(ErrorCode.APPLICATION);
            final var rows = (ListView<Achievement>) achievements;
            if (rows.getItems().getFirst().description().isBlank()
                    || rows.getItems().getFirst().description().equals("Hidden achievement")) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
            final var lockAll = (Button) view.lookup("#lock-all");
            unlockAll.fire();
            lockAll.fire();
            final var commit = (Button) view.lookup("#commit-preview");
            if (commit.isDisabled()) {
                unlockAll.fire();
            }
            if (commit.isDisabled())
                throw new StoneException(ErrorCode.APPLICATION);
            // Commit is real now. This read-only check must never fire it.
            ((Button) view.lookup("#reset-preview")).fire();
            if (!commit.isDisabled()) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
            final var checkbox = (CheckBox) achievements.lookup(".check-box");
            if (checkbox == null || checkbox.isDisabled())
                throw new StoneException(ErrorCode.APPLICATION);
            checkbox.fire();
            if (commit.isDisabled()) {
                throw new StoneException(ErrorCode.APPLICATION);
            }
            ((Button) view.lookup("#reset-preview")).fire();
            final var total = list.getItems().size();
            final var search = (TextField) view.lookup("#search-games");
            search.setText(Long.toString(list.getItems().getFirst().appId()));
            if (list.getItems().isEmpty() || list.getItems().size() > total)
                throw new StoneException(ErrorCode.APPLICATION);
            search.clear();
            if (list.getItems().size() != total)
                throw new StoneException(ErrorCode.APPLICATION);
            System.out.println(
                    "Graphical live check passed: " + total + " games; " + achievements.getItems().size() +
                            " achievements; checkbox, bulk preview, pending commit enablement (no write), reset, " +
                            "search and credential-field clearing verified.");
            view.close();
            Platform.exit();
        } catch (final Throwable e) {
            fail();
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }

    private void snapshot(final Path path) {
        try {
            view.applyCss();
            view.layout();
            final var snapshot = view.snapshot(null, null);
            final var width = (int) snapshot.getWidth();
            final var height = (int) snapshot.getHeight();
            final var pixels = new int[width * height];
            snapshot.getPixelReader()
                    .getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
            final var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, pixels, 0, width);
            ImageIO.write(image, "png", path.toFile());

        } catch (final IOException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }

    private void fail() {
        System.err.println("UI verification failed. No credential values or remote response bodies were logged.");
        if (poll != null)
            poll.stop();
        if (view != null)
            view.close();
        Platform.exit();
        System.exit(1);
    }

    @Override
    public void stop() {
        if (view != null)
            view.close();
    }
}
