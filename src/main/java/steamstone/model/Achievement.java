package steamstone.model;

/**
 * Steam's original account state, separate from any local checkbox preview.
 */
public record Achievement(String id, String name, String description, State state,
                          long unlockTime, String icon, String lockedIcon) {
    public enum State {LOCKED, UNLOCKED, UNKNOWN}
}
