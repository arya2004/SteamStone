package steamstone.ui;

import javafx.scene.image.Image;
import javafx.stage.Stage;
import steamstone.exception.ErrorCode;
import steamstone.exception.StoneException;

import java.util.List;

/**
 * Loads the shared artwork from the application resources in development and packaged builds.
 */
public final class AppIcon {
    private static final String RESOURCE_PATH = "/icons/steamstone.png";
    private static final List<Integer> WINDOW_ICON_SIZES = List.of(16, 32, 48, 64, 128, 256);

    public static Image load(final double size) {
        final var resource = AppIcon.class.getResource(RESOURCE_PATH);
        if (resource == null) {
            throw new StoneException(ErrorCode.APPLICATION);
        }
        try {
            final var image = new Image(resource.toExternalForm(), size, size, true, true);
            if (image.isError()) {
                throw new StoneException(ErrorCode.APPLICATION, image.getException());
            }
            return image;
        } catch (final IllegalArgumentException e) {
            throw new StoneException(ErrorCode.APPLICATION, e);
        }
    }

    public static void apply(final Stage stage) {
        stage.getIcons().setAll(WINDOW_ICON_SIZES.stream().map((final var size) -> load(size)).toList());
    }
}
