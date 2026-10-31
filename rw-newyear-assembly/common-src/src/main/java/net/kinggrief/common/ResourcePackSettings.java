package net.kinggrief.common;

import java.net.URL;

/** Shared by the grief and hub plugins. Never rewrite a custom pack URL. */
public final class ResourcePackSettings {
    public static final String LATEST_URL = "https://kinggrief.vercel.app/kinggrief-mega-rp-v72.zip";
    public static final String LATEST_SHA1 = "1729e4f29ad7fded64ab2f23c8b27f783936cd3d";

    private static final String V70_SHA1 = "8286228d4a1e0a95f56ad5fd265f12d1a57827be";
    private static final String V66_SHA1 = "63461884bd573de19a043f11b5982a34536e47d9";
    private static final String V63_SHA1 = "3637015af079e6fea621c10fe6dfa51e35cc0113";
    private static final String V65_SHA1 = "fc61175f0692ab85486898160dce223097aa4382";
    private static final String V64_SHA1 = "21b2343076c751bf392ac3bd47528c59988fb92c";

    private ResourcePackSettings() {}

    public static final class Pack {
        public final String url;
        public final String sha1;
        public final boolean upgraded;

        private Pack(String url, String sha1, boolean upgraded) {
            this.url = url;
            this.sha1 = sha1;
            this.upgraded = upgraded;
        }
    }

    /**
     * Upgrade only known, unmodified official v63/v64/v65/v66/v70 installations. A blank
     * SHA is also accepted for those exact URLs; an unknown SHA is preserved.
     * Config is not written, so server owners retain control of their settings.
     */
    public static Pack resolve(String url, String sha1) {
        if (url == null || url.trim().isEmpty()) return new Pack(url, sha1, false);
        try {
            URL parsed = new URL(url.trim());
            if (!"https".equalsIgnoreCase(parsed.getProtocol())
                    || !"kinggrief.vercel.app".equalsIgnoreCase(parsed.getHost())
                    || parsed.getUserInfo() != null || parsed.getRef() != null
                    || (parsed.getPort() != -1 && parsed.getPort() != 443)) {
                return new Pack(url, sha1, false);
            }
            String path = parsed.getPath();
            String query = parsed.getQuery();
            String expected = null;
            if ("/kinggrief-mega-rp.zip".equals(path)
                    && (query == null || "v=63".equals(query))) {
                expected = V63_SHA1;
            } else if ("/kinggrief-mega-rp-v64.zip".equals(path)
                    && (query == null || "v=64".equals(query))) {
                expected = V64_SHA1;
            } else if ("/kinggrief-mega-rp-v65.zip".equals(path)
                    && (query == null || "v=65".equals(query))) {
                expected = V65_SHA1;
            } else if ("/kinggrief-mega-rp-v66.zip".equals(path)
                    && (query == null || "v=66".equals(query))) {
                expected = V66_SHA1;
            } else if ("/kinggrief-mega-rp-v70.zip".equals(path)
                    && (query == null || "v=70".equals(query))) {
                expected = V70_SHA1;
            }
            String hash = sha1 == null ? "" : sha1.trim();
            if (expected != null && (hash.isEmpty() || expected.equalsIgnoreCase(hash))) {
                return new Pack(LATEST_URL, LATEST_SHA1, true);
            }
        } catch (Exception ignored) {
            // Invalid or custom URLs are passed through unchanged, not guessed.
        }
        return new Pack(url, sha1, false);
    }
}
