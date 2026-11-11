package steamstone;

import steamstone.steam.nativeapi.NativeRuntimeConstants;
import javafx.application.Application;
import steamstone.steam.nativeapi.NativeSteam;

/**
 * Separate launcher also works with jpackage's classpath launch mode.
 */
public final class Main {

    public static void main(final String[] args) {
        if (args.length == NativeRuntimeConstants.CHECK_RUNTIME_ARGUMENT_COUNT &&
                args[NativeRuntimeConstants.FIRST_ARGUMENT_INDEX].equals(
                        NativeRuntimeConstants.CHECK_RUNTIME_ARGUMENT)) {
            new NativeSteam().checkRuntime();
            return;
        }
        Application.launch(SteamStoneApp.class, args);
    }
}
