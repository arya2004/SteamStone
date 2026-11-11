package steamstone;

import javafx.application.Application;
import steamstone.steam.nativeapi.NativeSteam;

/**
 * Separate launcher also works with jpackage's classpath launch mode.
 */
public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        if (args.length == 1 && args[0].equals("--check-runtime")) {
            new NativeSteam().checkRuntime();
            return;
        }
        Application.launch(SteamStoneApp.class, args);
    }
}
