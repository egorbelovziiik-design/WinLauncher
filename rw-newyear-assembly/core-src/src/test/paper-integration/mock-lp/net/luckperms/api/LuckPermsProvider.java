package net.luckperms.api;
public final class LuckPermsProvider {
    private static LuckPerms instance;
    public static LuckPerms get() { if (instance == null) throw new IllegalStateException("not registered"); return instance; }
    public static void register(LuckPerms api) { instance = api; }
}
