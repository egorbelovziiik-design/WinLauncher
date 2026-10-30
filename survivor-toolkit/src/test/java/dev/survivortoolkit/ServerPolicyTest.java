package dev.survivortoolkit;

import dev.survivortoolkit.policy.ServerPolicy;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ServerPolicyTest {
    @Test void multiplayerRequiresPerFeatureAndExactAddress() {
        assertFalse(ServerPolicy.permitted(true, false, "example.org", Set.of()));
        assertTrue(ServerPolicy.permitted(true, false, " EXAMPLE.org ", Set.of("example.org")));
        assertFalse(ServerPolicy.permitted(true, false, "example.org:25565", Set.of("example.org")));
        assertFalse(ServerPolicy.permitted(true, false, null, Set.of("example.org")));
        assertFalse(ServerPolicy.permitted(true, false, "*.org", Set.of("*.org")));
        assertTrue(ServerPolicy.permitted(true, true, null, Set.of()));
    }
    @Test void freecamFailsClosedInMultiplayerAndOnDeath() {
        assertTrue(ServerPolicy.freecamPermitted(true, true, true));
        assertFalse(ServerPolicy.freecamPermitted(true, false, true));
        assertFalse(ServerPolicy.freecamPermitted(true, true, false));
        assertFalse(ServerPolicy.freecamPermitted(false, true, true));
    }
}
