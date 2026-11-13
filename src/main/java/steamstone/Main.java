package steamstone;

import javafx.application.Application;

import java.util.Arrays;

import steamstone.steam.nativeapi.NativeRuntimeConstants;
import steamstone.steam.nativeapi.NativeSteam;
import steamstone.steam.nativeapi.worker.NativeWorker;

/**
 * One application entry point for the editor and isolated native sessions.
 */
public final class Main {
    private static final int CHECK_RUNTIME_ARGUMENT_COUNT = 1;
    private static final int FIRST_ARGUMENT_INDEX = 0;
    private static final String WORKER_ARGUMENT = "--steam-worker";

    public static void main(final String[] args) {
        if (args.length > FIRST_ARGUMENT_INDEX && args[FIRST_ARGUMENT_INDEX].equals(WORKER_ARGUMENT)) {
            NativeWorker.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length == CHECK_RUNTIME_ARGUMENT_COUNT &&
                args[FIRST_ARGUMENT_INDEX].equals(
                        NativeRuntimeConstants.CHECK_RUNTIME_ARGUMENT)) {
            new NativeSteam().checkRuntime();
            return;
        }
        Application.launch(SteamStoneApp.class, args);
    }
}
