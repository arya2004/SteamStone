package steamstone.ui.scroll;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.EventHandler;
import javafx.scene.control.ListView;
import javafx.scene.control.skin.ListViewSkin;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

/**
 * Native pixel gestures pass straight through; discrete wheel ticks use JavaFX's standard easing.
 */
public final class SmoothListViewSkin<T> extends ListViewSkin<T> {
    private static final double LINE_PIXELS = 40;
    private static final Duration WHEEL_DURATION = Duration.millis(120);
    private Timeline animation;
    private double target;
    private boolean gesture;
    private final EventHandler<ScrollEvent> scrollHandler = this::scroll;
    private final EventHandler<ScrollEvent> gestureStarted = (final var event) -> {
        cancel();
        gesture = true;
    };
    private final EventHandler<ScrollEvent> gestureFinished = (final var event) -> gesture = false;
    private final EventHandler<InputEvent> interruptHandler = (final var event) -> cancel();

    public SmoothListViewSkin(final ListView<T> control) {
        super(control);
        control.setSnapToPixel(false);
        getVirtualFlow().setSnapToPixel(false);
        control.addEventFilter(ScrollEvent.SCROLL, scrollHandler);
        control.addEventFilter(ScrollEvent.SCROLL_STARTED, gestureStarted);
        control.addEventFilter(ScrollEvent.SCROLL_FINISHED, gestureFinished);
        control.addEventFilter(KeyEvent.KEY_PRESSED, interruptHandler);
        control.addEventFilter(MouseEvent.MOUSE_PRESSED, interruptHandler);
        registerChangeListener(control.itemsProperty(), (final var observable) -> cancel());
        registerListChangeListener(control.getItems(), (final var change) -> cancel());
        registerChangeListener(control.sceneProperty(), (final var observable) -> cancel());
        registerChangeListener(control.disabledProperty(), (final var observable) -> cancel());
    }

    private void scroll(final ScrollEvent event) {
        if (event.isControlDown() || event.isMetaDown() || event.isShiftDown()
                || getSkinnable().isDisabled() || event.getDeltaY() == 0)
            return;
        if (gesture || event.isInertia() || event.isDirect()
                || event.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.NONE) {
            cancel();
            getVirtualFlow().scrollPixels(-event.getDeltaY());
        } else {
            final var flow = getVirtualFlow();
            final var range = getSkinnable().getItems().size() * getSkinnable().getFixedCellSize() - flow.getHeight();
            if (range <= 0)
                return;
            final var pixels =
                    event.getTextDeltaY() * (event.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.PAGES
                            ? flow.getHeight() : LINE_PIXELS);
            final var current = flow.getPosition();
            final var base = animation != null && animation.getStatus() == Animation.Status.RUNNING
                    && Math.signum(target - current) == -Math.signum(pixels) ? target : current;
            cancel();
            target = Math.clamp(base - pixels / range, 0, 1);
            animation = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(flow.positionProperty(), current)),
                    new KeyFrame(WHEEL_DURATION, new KeyValue(flow.positionProperty(), target, Interpolator.EASE_OUT)));
            animation.play();
        }
        event.consume();
    }

    private void cancel() {
        if (animation != null) {
            final var position = getVirtualFlow().getPosition();
            animation.stop();
            getVirtualFlow().setPosition(position);
            animation = null;
        }
    }

    @Override
    public void dispose() {
        cancel();
        final var control = getSkinnable();
        control.removeEventFilter(ScrollEvent.SCROLL, scrollHandler);
        control.removeEventFilter(ScrollEvent.SCROLL_STARTED, gestureStarted);
        control.removeEventFilter(ScrollEvent.SCROLL_FINISHED, gestureFinished);
        control.removeEventFilter(KeyEvent.KEY_PRESSED, interruptHandler);
        control.removeEventFilter(MouseEvent.MOUSE_PRESSED, interruptHandler);
        super.dispose();
    }
}
