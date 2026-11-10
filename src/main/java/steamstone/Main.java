package steamstone;

/** Separate launcher also works with jpackage's classpath launch mode. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        javafx.application.Application.launch(SteamStoneApp.class, args);
    }
}
