package steamstone;

/**
 * Separate launcher also works with jpackage's classpath launch mode.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--check-runtime")) {
            new steamstone.steam.nativeapi.NativeSteam().checkRuntime();
            return;
        }
        javafx.application.Application.launch(SteamStoneApp.class, args);
    }
}
