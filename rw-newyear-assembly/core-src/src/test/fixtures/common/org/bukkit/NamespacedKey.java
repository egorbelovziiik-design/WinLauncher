package org.bukkit;
public final class NamespacedKey {
    private final String namespace, key;
    public NamespacedKey(String namespace, String key) { this.namespace = namespace; this.key = key; }
    public String getNamespace() { return namespace; } public String getKey() { return key; }
    @Override public boolean equals(Object o) { if (!(o instanceof NamespacedKey)) return false; NamespacedKey k = (NamespacedKey)o; return namespace.equals(k.namespace) && key.equals(k.key); }
    @Override public int hashCode() { return 31 * namespace.hashCode() + key.hashCode(); }
}
