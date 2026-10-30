package dev.survivortoolkit.policy;

import java.util.Locale;
import java.util.Set;

/** Fail closed: an unidentifiable remote server never inherits another server's consent. */
public final class ServerPolicy {
    private ServerPolicy() {}

    public static String normalize(String address) {
        if (address == null) return "";
        String value = address.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 255 || value.indexOf('*') >= 0 || value.indexOf('/') >= 0
                || value.chars().anyMatch(Character::isWhitespace) || value.chars().anyMatch(Character::isISOControl)) return "";
        return value;
    }

    public static boolean permitted(boolean worldActive, boolean privateLocal, String address, Set<String> consent) {
        if (!worldActive || privateLocal) return true;
        String normalized = normalize(address);
        return !normalized.isEmpty() && consent != null && consent.contains(normalized);
    }

    public static boolean freecamPermitted(boolean worldActive, boolean privateLocal, boolean playerAlive) {
        return worldActive && privateLocal && playerAlive;
    }
}
