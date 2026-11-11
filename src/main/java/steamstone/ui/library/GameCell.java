package steamstone.ui.library;

import java.util.Locale;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import steamstone.model.Game;

public final class GameCell extends ListCell<Game> {
    private final LibraryView view;
    private final Label initial = LibraryView.label("", "game-initial");
    private final ImageView image = new ImageView();
    private final Label name = LibraryView.label("", "game-name");
    private final HBox row;

    public GameCell(final LibraryView view) {
        this.view = view;
        image.setFitWidth(28);
        image.setFitHeight(28);
        final var cover = new StackPane(initial, image);
        cover.getStyleClass().add("game-cover");
        cover.setMinSize(28, 28);
        cover.setMaxSize(28, 28);
        final var text = new VBox(name);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        row = new HBox(10, cover, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("game-row");
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        setTooltip(new Tooltip());
    }

    @Override
    protected void updateItem(final Game game, final boolean empty) {
        super.updateItem(game, empty);
        if (empty || game == null) {
            image.setImage(null);
            getTooltip().setText("");
            setGraphic(null);
            setText(null);
            return;
        }
        initial.setText(game.name().substring(0, 1).toUpperCase(Locale.ROOT));
        image.setImage(view.iconFor(game));
        name.setText(game.name());
        getTooltip().setText(game.name());
        setGraphic(row);
        setText(null);
    }
}
