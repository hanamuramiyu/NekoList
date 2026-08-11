package hanamuramiyu.monban.velocity.session;

import com.velocitypowered.api.proxy.Player;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityConnectionIdentityRegistryTest {
    private static final UUID UUID_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_TWO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final VelocityConnectionIdentityRegistry registry = new VelocityConnectionIdentityRegistry();

    @Test
    void stagedIdentityIsNotActiveYet() {
        Player player = equalPlayerProxy();
        registry.stage(player, PlayerIdentity.online("hanamuramiyu", UUID_ONE));

        assertTrue(registry.findActive(player).isEmpty());
    }

    @Test
    void stagedIdentityCanBeActivated() {
        Player player = equalPlayerProxy();
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        registry.stage(player, identity);

        assertEquals(identity, registry.activate(player).orElseThrow());
        assertEquals(identity, registry.findActive(player).orElseThrow());
    }

    @Test
    void removeClearsPendingIdentity() {
        Player player = equalPlayerProxy();
        registry.stage(player, PlayerIdentity.online("hanamuramiyu", UUID_ONE));

        registry.remove(player);

        assertTrue(registry.activate(player).isEmpty());
        assertTrue(registry.findActive(player).isEmpty());
    }

    @Test
    void removeClearsActiveIdentity() {
        Player player = equalPlayerProxy();
        registry.stage(player, PlayerIdentity.online("hanamuramiyu", UUID_ONE));
        registry.activate(player).orElseThrow();

        registry.remove(player);

        assertTrue(registry.findActive(player).isEmpty());
    }

    @Test
    void activatingMissingConnectionReturnsEmpty() {
        assertTrue(registry.activate(equalPlayerProxy()).isEmpty());
    }

    @Test
    void distinctConnectionInstancesRemainIndependentEvenWhenEqualsAndHashCodeMatch() {
        Player first = equalPlayerProxy();
        Player second = equalPlayerProxy();
        PlayerIdentity firstIdentity = PlayerIdentity.online("hanamuramiyu", UUID_ONE);
        PlayerIdentity secondIdentity = PlayerIdentity.online("hanamuramiyu2", UUID_TWO);

        assertNotSame(first, second);
        assertTrue(first.equals(second));
        assertEquals(first.hashCode(), second.hashCode());

        registry.stage(first, firstIdentity);
        registry.stage(second, secondIdentity);

        assertEquals(firstIdentity, registry.activate(first).orElseThrow());
        assertFalse(registry.findActive(second).isPresent());
        assertEquals(secondIdentity, registry.activate(second).orElseThrow());
        assertEquals(firstIdentity, registry.findActive(first).orElseThrow());
        assertEquals(secondIdentity, registry.findActive(second).orElseThrow());
    }

    private static Player equalPlayerProxy() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> true;
                    case "hashCode" -> 7;
                    case "toString" -> "EqualPlayerProxy";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        return 0.0d;
    }
}
