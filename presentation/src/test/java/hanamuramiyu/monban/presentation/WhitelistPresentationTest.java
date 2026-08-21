package hanamuramiyu.monban.presentation;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitelistPresentationTest {
    private static final UUID VERIFIED_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final WhitelistPresentation presentation = new WhitelistPresentation();

    @Test
    void generatedListCommandsPreserveFilters() {
        assertEquals("/monban whitelist list 2", presentation.listCommand(null, 2));
        assertEquals("/monban whitelist list offline 3", presentation.listCommand(IdentityType.OFFLINE, 3));
        assertEquals("/monban whitelist list online 4", presentation.listCommand(IdentityType.ONLINE, 4));
    }

    @Test
    void paginationCommandsPreserveActiveFilter() {
        List<AccessGrant> grants = new ArrayList<>();
        for (int index = 1; index <= 21; index++) {
            grants.add(new AccessGrant(
                    AccessScope.network(),
                    PlayerIdentity.offline("player" + String.format("%02d", index))
            ));
        }

        WhitelistListView view = presentation.listing(grants, IdentityType.OFFLINE, 2);
        assertTrue(view.successful());
        assertTrue(clickEvents(view.lines()).stream().anyMatch(event ->
                event.action() == ClickEvent.Action.RUN_COMMAND
                        && event.value().equals("/monban whitelist list offline 1")
        ));
        assertTrue(clickEvents(view.lines()).stream().anyMatch(event ->
                event.action() == ClickEvent.Action.RUN_COMMAND
                        && event.value().equals("/monban whitelist list offline 3")
        ));
        assertTrue(visibleText(view.lines()).contains("11–20 of 21"));
    }

    @Test
    void onlineUuidIsCompactInVisibleTextAndFullUuidIsCopyable() {
        WhitelistListView view = presentation.listing(
                List.of(new AccessGrant(
                        AccessScope.network(),
                        PlayerIdentity.online("hanamuramiyu", VERIFIED_UUID)
                )),
                null,
                1
        );

        String visible = visibleText(view.lines());
        List<ClickEvent> clicks = clickEvents(view.lines());

        assertTrue(visible.contains("[ONLINE] hanamuramiyu · 00000000…0001"));
        assertFalse(visible.contains(VERIFIED_UUID.toString()));
        assertTrue(clicks.stream().anyMatch(event ->
                event.action() == ClickEvent.Action.COPY_TO_CLIPBOARD
                        && event.value().equals(VERIFIED_UUID.toString())
        ));
    }

    @Test
    void emptyAndOutOfRangeStatesAreExplicit() {
        WhitelistListView empty = presentation.listing(List.of(), IdentityType.ONLINE, 1);
        assertTrue(empty.successful());
        assertTrue(visibleText(empty.lines()).contains("No [ONLINE] whitelist entries found."));

        WhitelistListView outOfRange = presentation.listing(
                List.of(new AccessGrant(AccessScope.network(), PlayerIdentity.offline("hanamuramiyu"))),
                IdentityType.OFFLINE,
                2
        );
        assertFalse(outOfRange.successful());
        assertTrue(visibleText(outOfRange.lines()).contains("Page 2 is out of range. Available pages: 1-1."));
        assertTrue(clickEvents(outOfRange.lines()).stream().anyMatch(event ->
                event.action() == ClickEvent.Action.RUN_COMMAND
                        && event.value().equals("/monban whitelist list offline 1")
        ));

        WhitelistListView emptyOutOfRange = presentation.listing(List.of(), IdentityType.ONLINE, 2);
        assertFalse(emptyOutOfRange.successful());
        assertTrue(visibleText(emptyOutOfRange.lines()).contains("Page 2 is out of range. Available pages: 1-1."));
    }

    @Test
    void reusableIdentityAndScopeBadgesUseSharedVisualLanguage() {
        MonbanUi ui = new MonbanUi();

        assertEquals("[ONLINE]", visibleText(List.of(ui.identityBadge(IdentityType.ONLINE))));
        assertEquals("[OFFLINE]", visibleText(List.of(ui.identityBadge(IdentityType.OFFLINE))));
        assertEquals("[NETWORK]", visibleText(List.of(ui.scopeBadge(AccessScope.network()))));
        assertEquals("[GROUP:testing]", visibleText(List.of(ui.scopeBadge(AccessScope.serverGroup("testing")))));
        assertEquals("[SERVER:lobby]", visibleText(List.of(ui.scopeBadge(AccessScope.server("lobby")))));
    }

    private static List<ClickEvent> clickEvents(List<Component> components) {
        List<ClickEvent> events = new ArrayList<>();
        components.forEach(component -> collectClicks(component, events));
        return events;
    }

    private static void collectClicks(Component component, List<ClickEvent> events) {
        if (component.clickEvent() != null) {
            events.add(component.clickEvent());
        }
        component.children().forEach(child -> collectClicks(child, events));
    }

    private static String visibleText(List<Component> components) {
        StringBuilder builder = new StringBuilder();
        for (Component component : components) {
            appendVisibleText(component, builder);
            builder.append('\n');
        }
        if (!builder.isEmpty()) {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    private static void appendVisibleText(Component component, StringBuilder builder) {
        if (component instanceof TextComponent text) {
            builder.append(text.content());
        }
        component.children().forEach(child -> appendVisibleText(child, builder));
    }
}
