package com.lt5lyrics.client.island;
import java.util.Locale;
/** The owner identifies an app/browser, not which website it is playing. */
public final class PlayerBadge {
    private PlayerBadge() {}
    public static String detect(String owner) {
        if(owner==null)return null; String s=owner.toLowerCase(Locale.ROOT);
        if(s.contains("spotify"))return "Spotify";
        if(s.contains("msedge") || s.contains("microsoftedge"))return "Edge";
        if(s.contains("chrome"))return "Chrome";
        if(s.contains("firefox")||s.contains("308046b0af4a39cb"))return "Firefox";
        if(s.contains("brave"))return "Brave";
        if(s.contains("opera"))return "Opera";
        if(s.contains("vivaldi"))return "Vivaldi";
        if(s.contains("yandex"))return "Yandex Music";
        if(s.contains("soundcloud"))return "SoundCloud";
        if(s.contains("telegram"))return "Telegram";
        if(s.contains("youtube"))return "YouTube";
        return null;
    }
}
