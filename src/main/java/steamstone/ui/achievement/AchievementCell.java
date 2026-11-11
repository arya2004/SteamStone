package steamstone.ui.achievement;

import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import steamstone.model.achievement.Achievement;
import steamstone.model.achievement.AchievementState;

public final class AchievementCell extends ListCell<Achievement> {
    private final AchievementEditor editor;
    private final CheckBox check = new CheckBox();
    private final ImageView image = new ImageView();
    private final Label name = AchievementEditor.label("", "achievement-name");
    private final Label description = AchievementEditor.label("", "achievement-description");
    private final Label state = AchievementEditor.label("", "achievement-state");
    private final HBox row;

    public AchievementCell(final AchievementEditor editor) {
        this.editor = editor;
        image.setFitWidth(32);
        image.setFitHeight(32);
        final var cover = new StackPane(image);
        cover.setMinSize(32, 32);
        cover.setMaxSize(32, 32);
        cover.getStyleClass().add("achievement-cover");
        final var text = new VBox(3, name, description);
        text.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        description.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        row = new HBox(10, check, cover, text, state);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("achievement-row");
        check.setOnAction((final var e) -> {
            if (getItem() != null) {
                editor.preview().setUnlocked(getItem(), check.isSelected());
                editor.changed();
            }
        });
    }

    @Override
    protected void updateItem(final Achievement achievement, final boolean emptyCell) {
        super.updateItem(achievement, emptyCell);
        setText(null);
        row.getStyleClass().removeAll("unlocked-row", "locked-row", "unknown-row", "changed-row");
        if (emptyCell || achievement == null) {
            setGraphic(null);
            image.setImage(null);
            return;
        }
        final var unknown = achievement.state() == AchievementState.UNKNOWN;
        final var unlocked = editor.preview().isUnlocked(achievement);
        final var edited = editor.preview().isChanged(achievement);
        row.getStyleClass().add(unknown ? "unknown-row" : unlocked ? "unlocked-row" : "locked-row");
        if (edited)
            row.getStyleClass().add("changed-row");
        check.setSelected(unlocked);
        check.setIndeterminate(unknown);
        check.setDisable(unknown || !editor.nativeReady() || editor.committingProperty().get());
        check.setAccessibleText("Unlock state: " + achievement.name());
        image.setImage(editor.icon(achievement));
        name.setText(achievement.name());
        description.setText(achievement.description());
        description.setVisible(!achievement.description().isBlank());
        description.setManaged(description.isVisible());
        description.setTooltip(new Tooltip(achievement.description()));
        name.setTooltip(new Tooltip(achievement.name() + "\n" + achievement.id()));
        state.setText(unknown ? "UNAVAILABLE" : (edited ? "• " : "") + (unlocked ? "UNLOCKED" : "LOCKED"));
        setGraphic(row);
        row.prefWidthProperty().bind(widthProperty().subtract(4));
    }
}
