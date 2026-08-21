package hanamuramiyu.monban.presentation;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LookupPresentationTest {
    private static final UUID VERIFIED_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void onlineWhitelistTakesPrecedenceInNetworkResult() {
        PlayerIdentity offline = PlayerIdentity.offline("hanamuramiyu");
        PlayerIdentity online = PlayerIdentity.online("hanamuramiyu", VERIFIED_UUID);
        LookupPresentation presentation = new LookupPresentation();

        List<Component> lines = presentation.result(
                offline,
                Optional.of(online),
                List.of(new AccessGrant(AccessScope.network(), online), new AccessGrant(AccessScope.network(), offline))
        );

        String visible = visibleText(lines);
        assertTrue(visible.contains("Player · hanamuramiyu"));
        assertTrue(visible.contains("ONLINE 00000000…0001"));
        assertTrue(visible.contains("OFFLINE hanamuramiyu"));
        assertTrue(visible.contains("Whitelisted as ONLINE."));
    }

    @Test
    void offlineWhitelistIsReportedWhenOnlineProfileIsMissing() {
        PlayerIdentity offline = PlayerIdentity.offline("miyu2");
        String visible = visibleText(new LookupPresentation().result(
                offline,
                Optional.empty(),
                List.of(new AccessGrant(AccessScope.network(), offline))
        ));

        assertTrue(visible.contains("Whitelisted as OFFLINE."));
        assertTrue(visible.contains("OFFLINE miyu2"));
    }

    @Test
    void missingNetworkGrantIsReported() {
        PlayerIdentity offline = PlayerIdentity.offline("miyu2");
        String visible = visibleText(new LookupPresentation().result(
                offline,
                Optional.empty(),
                List.of()
        ));

        assertTrue(visible.contains("Not whitelisted."));
    }

    private static String visibleText(List<Component> components) {
        StringBuilder result = new StringBuilder();
        components.forEach(component -> appendVisibleText(component, result));
        return result.toString();
    }

    private static void appendVisibleText(Component component, StringBuilder result) {
        if (component instanceof TextComponent text) {
            result.append(text.content());
        }
        component.children().forEach(child -> appendVisibleText(child, result));
    }
}
