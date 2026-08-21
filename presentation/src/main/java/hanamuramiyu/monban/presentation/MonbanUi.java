package hanamuramiyu.monban.presentation;

import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Objects;
import java.util.UUID;

public final class MonbanUi {
    private static final Component PREFIX = Component.empty()
            .append(Component.text("monban", MonbanPalette.BRAND).decorate(TextDecoration.BOLD))
            .append(Component.text(" › ", MonbanPalette.SUBTLE));

    public Component success(Component content) {
        return semantic("✓", MonbanPalette.SUCCESS, content);
    }

    public Component error(Component content) {
        return semantic("✕", MonbanPalette.ERROR, content);
    }

    public Component warning(Component content) {
        return semantic("!", MonbanPalette.WARNING, content);
    }

    public Component info(Component content) {
        return semantic("i", MonbanPalette.INFO, content);
    }

    public Component unknownCommand() {
        return text("Unknown command. Type \"/help\" for help.");
    }

    public Component separator() {
        return muted("────────────────────────────");
    }

    public Component section(String label) {
        return Component.text(Objects.requireNonNull(label, "label"), MonbanPalette.SECONDARY)
                .decorate(TextDecoration.BOLD);
    }

    public Component title(String section) {
        Objects.requireNonNull(section, "section");
        return PREFIX.append(Component.text(section, MonbanPalette.TEXT).decorate(TextDecoration.BOLD));
    }

    public Component label(String label, Component value) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        return Component.text(label, MonbanPalette.MUTED)
                .append(Component.text(": ", MonbanPalette.SUBTLE))
                .append(value);
    }

    public Component identityBadge(IdentityType type) {
        Objects.requireNonNull(type, "type");
        return badge(type.name(), type == IdentityType.ONLINE ? MonbanPalette.ONLINE : MonbanPalette.OFFLINE);
    }

    public Component scopeBadge(AccessScope scope) {
        Objects.requireNonNull(scope, "scope");
        String value = switch (scope.type()) {
            case NETWORK -> "NETWORK";
            case SERVER_GROUP -> "GROUP:" + scope.id().orElseThrow();
            case SERVER -> "SERVER:" + scope.id().orElseThrow();
        };
        return badge(value, scope.type() == hanamuramiyu.monban.access.scope.AccessScopeType.NETWORK
                ? MonbanPalette.NETWORK
                : scope.type() == hanamuramiyu.monban.access.scope.AccessScopeType.SERVER_GROUP
                ? MonbanPalette.SERVER_GROUP
                : MonbanPalette.SERVER);
    }

    public Component scopeText(AccessScope scope) {
        Objects.requireNonNull(scope, "scope");
        String value = switch (scope.type()) {
            case NETWORK -> "NETWORK";
            case SERVER_GROUP -> "SERVER_GROUP(" + scope.id().orElseThrow() + ")";
            case SERVER -> "SERVER(" + scope.id().orElseThrow() + ")";
        };
        return Component.text(value, MonbanPalette.SCOPE);
    }

    public Component identity(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        Component result = Component.empty()
                .append(identityBadge(identity.type()))
                .append(Component.text(" "))
                .append(Component.text(identity.name(), MonbanPalette.TEXT));
        if (identity.type() == IdentityType.ONLINE) {
            result = result
                    .append(Component.text(" · ", MonbanPalette.SUBTLE))
                    .append(compactUuid(identity.verifiedUuid().orElseThrow()));
        }
        return result;
    }

    public Component command(String command) {
        Objects.requireNonNull(command, "command");
        return Component.text(command, MonbanPalette.BRAND)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text("Click to suggest", MonbanPalette.MUTED)));
    }

    public Component pageButton(String label, String command, boolean enabled) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(command, "command");
        if (!enabled) {
            return Component.text(label, MonbanPalette.DISABLED);
        }
        return Component.text(label, MonbanPalette.BRAND).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(command, MonbanPalette.MUTED)));
    }

    public Component muted(String text) {
        return Component.text(Objects.requireNonNull(text, "text"), MonbanPalette.MUTED);
    }

    public Component text(String text) {
        return Component.text(Objects.requireNonNull(text, "text"), MonbanPalette.TEXT);
    }

    public Component fullIdentity(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        Component result = Component.empty()
                .append(Component.text(identity.type().name(), identity.type() == IdentityType.ONLINE
                        ? MonbanPalette.ONLINE : MonbanPalette.OFFLINE))
                .append(Component.text(" "))
                .append(Component.text(identity.name(), MonbanPalette.TEXT));
        if (identity.type() == IdentityType.ONLINE) {
            result = result.append(Component.text(" (" + identity.verifiedUuid().orElseThrow() + ")", MonbanPalette.UUID));
        }
        return result;
    }

    public Component lookupIdentity(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        if (identity.type() == IdentityType.ONLINE) {
            return Component.empty()
                    .append(Component.text("ONLINE ", MonbanPalette.ONLINE))
                    .append(compactUuid(identity.verifiedUuid().orElseThrow()));
        }
        return Component.empty()
                .append(Component.text("OFFLINE ", MonbanPalette.OFFLINE))
                .append(Component.text(identity.name(), MonbanPalette.TEXT));
    }

    private Component semantic(String marker, TextColor color, Component content) {
        Objects.requireNonNull(content, "content");
        return PREFIX
                .append(Component.text(marker, color).decorate(TextDecoration.BOLD))
                .append(Component.text(" "))
                .append(content);
    }

    private Component badge(String label, TextColor color) {
        return Component.text("[", MonbanPalette.SUBTLE)
                .append(Component.text(label, color).decorate(TextDecoration.BOLD))
                .append(Component.text("]", MonbanPalette.SUBTLE));
    }

    private Component compactUuid(UUID uuid) {
        String full = uuid.toString();
        String compact = full.substring(0, 8) + "…" + full.substring(full.length() - 4);
        Component hover = Component.text(full, MonbanPalette.TEXT)
                .append(Component.text("\n"))
                .append(Component.text("Click to copy UUID", MonbanPalette.MUTED));
        return Component.text(compact, MonbanPalette.UUID)
                .clickEvent(ClickEvent.copyToClipboard(full))
                .hoverEvent(HoverEvent.showText(hover));
    }
}
