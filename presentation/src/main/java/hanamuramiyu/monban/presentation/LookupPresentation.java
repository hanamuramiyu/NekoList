package hanamuramiyu.monban.presentation;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.effective.AccessOrigin;
import hanamuramiyu.monban.access.effective.AccessOriginType;
import hanamuramiyu.monban.access.effective.EffectivePermission;
import hanamuramiyu.monban.access.effective.PlayerAccessSnapshot;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Comparator;
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
        return result(offlineIdentity, onlineIdentity, grants, null);
    }

    public List<Component> result(
            PlayerIdentity offlineIdentity,
            Optional<PlayerIdentity> onlineIdentity,
            List<AccessGrant> grants,
            PlayerAccessSnapshot snapshot
    ) {
        Objects.requireNonNull(offlineIdentity, "offlineIdentity");
        Objects.requireNonNull(onlineIdentity, "onlineIdentity");
        Objects.requireNonNull(grants, "grants");

        Optional<AccessGrant> offlineGrant = grantFor(grants, offlineIdentity);
        Optional<AccessGrant> onlineGrant = onlineIdentity.flatMap(identity -> grantFor(grants, identity));
        String displayName = onlineIdentity.map(PlayerIdentity::name).orElse(offlineIdentity.name());

        List<Component> lines = new ArrayList<>();
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
        if (snapshot != null) {
            addGroups(lines, snapshot);
            addAccess(lines, snapshot);
            addPermissions(lines, snapshot);
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

    private void addGroups(List<Component> lines, PlayerAccessSnapshot snapshot) {
        lines.add(ui.section("Groups"));
        if (snapshot.groups().isEmpty()) {
            lines.add(indent(ui.muted("None.")));
            return;
        }
        snapshot.groups().stream()
                .map(PlayerGroupDefinition::id)
                .sorted()
                .forEach(group -> lines.add(indent(ui.text(group))));
    }

    private void addAccess(List<Component> lines, PlayerAccessSnapshot snapshot) {
        lines.add(ui.section("Effective access"));
        if (snapshot.accesses().isEmpty()) {
            lines.add(indent(ui.muted("None.")));
            return;
        }
        snapshot.accesses().stream()
                .sorted(Comparator.comparing(access -> access.scope().toString()))
                .forEach(access -> lines.add(indent(Component.empty()
                        .append(ui.scopeText(access.scope()))
                        .append(ui.text(" — "))
                        .append(ui.text(origins(access.origins()))))));
    }

    private void addPermissions(List<Component> lines, PlayerAccessSnapshot snapshot) {
        lines.add(ui.section("Effective permissions"));
        if (snapshot.permissions().isEmpty()) {
            lines.add(indent(ui.muted("None.")));
            return;
        }
        snapshot.permissions().stream()
                .sorted(Comparator.comparing((EffectivePermission permission) -> permission.grant().scope().toString())
                        .thenComparing(permission -> permission.grant().node()))
                .forEach(permission -> lines.add(indent(Component.empty()
                        .append(ui.scopeText(permission.grant().scope()))
                        .append(ui.text(" "))
                        .append(ui.text(permission.grant().node()))
                        .append(ui.text(" — "))
                        .append(ui.text(origins(permission.origins()))))));
    }

    private String origins(List<AccessOrigin> origins) {
        return origins.stream()
                .map(origin -> origin.type() == AccessOriginType.DIRECT
                        ? "DIRECT"
                        : "GROUP:" + origin.groupId().orElseThrow())
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }
}
