package net.luckperms.api.model.user;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
public interface UserManager {
    User getUser(UUID id);
    CompletableFuture<UUID> lookupUniqueId(String name);
    CompletableFuture<User> loadUser(UUID id);
}
