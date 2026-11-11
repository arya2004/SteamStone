package steamstone.ui.verification;

import javafx.application.Application;

/**
 * Optional graphical/read-only integration check, excluded from the shipped application.
 */
public final class UiSmokeCheck {
    public static void main(String[] args) {
        Application.launch(CheckApplication.class, args);
    }

}
