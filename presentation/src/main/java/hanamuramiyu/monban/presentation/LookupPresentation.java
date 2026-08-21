package hanamuramiyu.monban.presentation;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LookupPresentation {
    private final MonbanUi ui;

    public LookupPresentation() {
        this(new MonbanUi());
    }

    public LookupPresentation(MonbanUi ui) {
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    public List<Component> result(
            PlayerIdentity offlineIdentity,
            Optional<PlayerIdentity> onlineIdentity,
            List<AccessGrant> grants
    ) {
        Objects.requireNonNull(offlineIdentity, "offlineIdentity");
        Objects.requireNonNull(onlineIdentity, "onlineIdentity");
        Objects.requireNonNull(grants, "grants");

        Optional<AccessGrant> offlineGrant = grantFor(grants, offlineIdentity);
        Optional<AccessGrant> onlineGrant = onlineIdentity.flatMap(identity -> grantFor(grants, identity));
        String displayName = onlineIdentity.map(PlayerIdentity::name).orElse(offlineIdentity.name());

        List<Component> lines = new ArrayList<>(8);
        lines.add(ui.title("Player · " + displayName));
        lines.add(ui.separator());
        lines.add(ui.section("Identity"));
        onlineIdentity.ifPresent(identity -> lines.add(indent(ui.lookupIdentity(identity))));
        lines.add(indent(ui.lookupIdentity(offlineIdentity)));
        lines.add(ui.section("Network"));
        if (onlineGrant.isPresent()) {
            lines.add(ui.success(ui.text("Whitelisted as ONLINE.")));
        } else if (offlineGrant.isPresent()) {
            lines.add(ui.success(ui.text("Whitelisted as OFFLINE.")));
        } else {
            lines.add(ui.info(ui.text("Not whitelisted.")));
        }
        return List.copyOf(lines);
    }

    public Component invalidPlayerName(String name) {
        return ui.error(Component.empty()
                .append(ui.text("Invalid Minecraft player name: "))
                .append(Component.text(Objects.requireNonNull(name, "name"), MonbanPalette.WARNING))
                .append(ui.text(".")));
    }

    public Component readFailure() {
        return ui.error(ui.text("Failed to read the monban whitelist. Check the proxy log."));
    }

    public Component lookupFailure() {
        return ui.error(ui.text("Failed to look up the player. Check the proxy log."));
    }

    public Component onlineProfileUnavailable() {
        return ui.warning(ui.text("Online profile lookup is temporarily unavailable. Showing OFFLINE identity only."));
    }

    private Optional<AccessGrant> grantFor(List<AccessGrant> grants, PlayerIdentity identity) {
        return grants.stream()
                .filter(grant -> grant.scope().equals(AccessScope.network()))
                .filter(grant -> grant.identity().equals(identity))
                .findFirst();
    }

    private Component indent(Component component) {
        return Component.empty()
                .append(ui.text("  "))
                .append(component);
    }
}
