package org.bukkit.persistence;
import org.bukkit.NamespacedKey;
public interface PersistentDataContainer { <P,C> C get(NamespacedKey key, PersistentDataType<P,C> type); }
