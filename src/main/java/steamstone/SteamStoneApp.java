package steamstone;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import steamstone.inject.SteamStoneInjector;
import steamstone.ui.library.LibraryView;

public final class SteamStoneApp extends Application {
    private LibraryView libraryView;

    @Override
    public void start(final Stage stage) {
        libraryView = SteamStoneInjector.createView();
        final var scene = new Scene(libraryView, 1180, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        stage.setTitle("SteamStone");
        stage.setMinWidth(940);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.setOnCloseRequest((final var event) -> {
            if (!libraryView.canClose())
                event.consume();
        });
        stage.show();
    }

    @Override
    public void stop() {
        if (libraryView != null)
            libraryView.close();
    }
}
