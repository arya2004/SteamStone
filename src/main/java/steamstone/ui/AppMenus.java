package steamstone.ui;

import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCombination;

/**
 * Menu commands share the buttons' actions and availability.
 */
public final class AppMenus {
    public static MenuItem command(final String text, final String shortcut, final Runnable action) {
        final var item = new MenuItem(text);
        if (shortcut != null)
            item.setAccelerator(KeyCombination.keyCombination(shortcut));
        item.setOnAction((final var event) -> action.run());
        return item;
    }

    public static MenuItem action(final String text, final String shortcut, final Button button) {
        final var item = command(text, shortcut, button::fire);
        item.disableProperty().bind(button.disabledProperty());
        return item;
    }
}
