package ru.reallyworld.ultimate.core.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Generates and verifies HMAC-SHA256 signatures stored in PersistentDataContainer
 * to guarantee custom spheres, talismans, artifacts, weapons, armor, and consumables
 * cannot be forged via anvil renaming or unauthorized client item packets.
 */
public final class CryptoSignatureUtil {
    private static final String HMAC_ALGO = "HmacSHA256";

    private CryptoSignatureUtil() {}

    public static String signItem(String salt, String category, String itemId, String tier) {
        String effectiveSalt = (salt == null || salt.isBlank()) ? "rw-default-salt" : salt;
        String payload = category + ":" + itemId + ":" + (tier == null ? "BASE" : tier);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(effectiveSalt.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign custom item payload", e);
        }
    }

    public static boolean verifyItem(String salt, String category, String itemId, String tier, String signature) {
        if (signature == null || signature.isBlank() || category == null || itemId == null) {
            return false;
        }
        String expected = signItem(salt, category, itemId, tier);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
