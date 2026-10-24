package nexus.core.profile;

import nexus.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Small user profile owned by the client: the nickname shown by the client, the
 * toggle for the custom main menu and a couple of comfort options.
 * <p>
 * Everything is stored in {@code <config>/profile.properties} so it survives
 * restarts even when the JSON config is reset.
 */
public final class ClientProfile {

    private static final String FILE_NAME = "profile.properties";
    private static final int MAX_NICKNAME = 16;

    private final File file;

    private String nickname = "";
    private boolean customMainMenu = true;
    private boolean showModuleList = true;
    private boolean notifications = true;

    public ClientProfile(File directory) {
        this.file = new File(directory, FILE_NAME);
        load();
    }

    // ------------------------------------------------------------------ storage

    public synchronized void load() {
        if (!file.isFile()) {
            return;
        }
        Properties properties = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            properties.load(in);
            nickname = properties.getProperty("nickname", nickname);
            customMainMenu = Boolean.parseBoolean(properties.getProperty("customMainMenu", String.valueOf(customMainMenu)));
            showModuleList = Boolean.parseBoolean(properties.getProperty("showModuleList", String.valueOf(showModuleList)));
            notifications = Boolean.parseBoolean(properties.getProperty("notifications", String.valueOf(notifications)));
        } catch (Throwable throwable) {
            Log.warn("Could not read " + file.getName() + ": " + throwable.getMessage());
        } finally {
            close(in);
        }
    }

    public synchronized void save() {
        Properties properties = new Properties();
        properties.setProperty("nickname", nickname);
        properties.setProperty("customMainMenu", String.valueOf(customMainMenu));
        properties.setProperty("showModuleList", String.valueOf(showModuleList));
        properties.setProperty("notifications", String.valueOf(notifications));
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            Log.warn("Could not create " + parent);
            return;
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            properties.store(out, "Nexus Client Omega X - user profile");
        } catch (IOException exception) {
            Log.warn("Could not write " + file.getName() + ": " + exception.getMessage());
        } finally {
            close(out);
        }
    }

    private static void close(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // nothing we can do about it
        }
    }

    // ----------------------------------------------------------------- nickname

    public String nickname() {
        return nickname == null ? "" : nickname;
    }

    /** Nickname or the vanilla account name when the user did not set one. */
    public String displayName(String fallback) {
        String name = nickname();
        return name.isEmpty() ? fallback : name;
    }

    public void setNickname(String value) {
        String cleaned = sanitize(value);
        this.nickname = cleaned;
        save();
    }

    /** Keeps only characters a Minecraft name may contain, max 16 of them. */
    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(MAX_NICKNAME);
        for (int i = 0; i < value.length() && builder.length() < MAX_NICKNAME; i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_';
            if (allowed) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    // ----------------------------------------------------------------- options

    public boolean customMainMenu() {
        return customMainMenu;
    }

    public void setCustomMainMenu(boolean value) {
        this.customMainMenu = value;
        save();
    }

    public boolean showModuleList() {
        return showModuleList;
    }

    public void setShowModuleList(boolean value) {
        this.showModuleList = value;
        save();
    }

    public boolean notifications() {
        return notifications;
    }

    public void setNotifications(boolean value) {
        this.notifications = value;
        save();
    }

    public File file() {
        return file;
    }
}
