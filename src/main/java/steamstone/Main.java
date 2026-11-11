package steamstone;

import steamstone.steam.nativeapi.NativeRuntimeConstants;
import javafx.application.Application;
import steamstone.steam.nativeapi.NativeSteam;

/**
 * Separate launcher also works with jpackage's classpath launch mode.
 */
public final class Main {
    private static final int CHECK_RUNTIME_ARGUMENT_COUNT = 1;
    private static final int FIRST_ARGUMENT_INDEX = 0;

    public static void main(final String[] args) {
        if (args.length == CHECK_RUNTIME_ARGUMENT_COUNT &&
                args[FIRST_ARGUMENT_INDEX].equals(
                        NativeRuntimeConstants.CHECK_RUNTIME_ARGUMENT)) {
            new NativeSteam().checkRuntime();
            return;
        }
        Application.launch(SteamStoneApp.class, args);
    }
}
