package com.mozilla.util;

public class AvatarUtils {
    public static String getAvatarUrl(String uuid) {
        return "https://api.mineatar.io/face/" + uuid + "?scale=32";
    }

    public static String getFullBodyUrl(String uuid) {
        return "https://api.mineatar.io/body/full/" + uuid + "?scale=32";
    }
}
