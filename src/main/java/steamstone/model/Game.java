package steamstone.model;

public record Game(long appId, String name, long playtimeMinutes, long lastPlayed, String iconHash) {
    public String iconUrl() {
        if (iconHash == null || !iconHash.matches("[a-fA-F0-9]{40}"))
            return null;
        return "https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/apps/"
                + appId + "/" + iconHash + ".jpg";
    }
}
