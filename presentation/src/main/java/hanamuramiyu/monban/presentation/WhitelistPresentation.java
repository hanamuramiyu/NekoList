package hanamuramiyu.monban.presentation;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class WhitelistPresentation {
    public static final int PAGE_SIZE = 10;

    private static final Comparator<AccessGrant> WHITELIST_ORDER = Comparator
            .comparing((AccessGrant grant) -> grant.identity().type().name())
            .thenComparing(grant -> grant.identity().normalizedName())
            .thenComparing(grant -> grant.identity().verifiedUuid().map(UUID::toString).orElse(""));

    private final MonbanUi ui;

    public WhitelistPresentation() {
        this(new MonbanUi());
    }

    public WhitelistPresentation(MonbanUi ui) {
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    public Component permissionDenied() {
        return ui.error(Component.empty()
                .append(ui.text("You do not have permission to use "))
                .append(ui.command("/monban whitelist"))
                .append(ui.text(".")));
    }

    public List<Component> usage() {
        return List.of(
                ui.title("whitelist"),
                usageLine("Add offline", "/monban whitelist add offline <name>"),
                usageLine("Add online", "/monban whitelist add online <name>"),
                usageLine("Add online · UUID", "/monban whitelist add online <name> <uuid>"),
                usageLine("Remove offline", "/monban whitelist remove offline <name>"),
                usageLine("Remove online", "/monban whitelist remove online <name>"),
                usageLine("Remove online · UUID", "/monban whitelist remove online <name> <uuid>"),
                usageLine("Enable", "/monban whitelist enable"),
                usageLine("Disable", "/monban whitelist disable"),
                usageLine("List", "/monban whitelist list [page]"),
                usageLine("Filter", "/monban whitelist list <offline|online> [page]")
        );
    }

    public Component invalidUuid(String value) {
        return ui.error(Component.empty()
                .append(ui.text("Invalid UUID: "))
                .append(Component.text(Objects.requireNonNull(value, "value"), MonbanPalette.WARNING))
                .append(ui.text(".")));
    }

    public Component invalidPlayerName(String name) {
        return ui.error(Component.empty()
                .append(ui.text("Invalid Minecraft player name: "))
                .append(Component.text(Objects.requireNonNull(name, "name"), MonbanPalette.WARNING))
                .append(ui.text(".")));
    }

    public Component invalidIdentityType(String value) {
        return ui.error(Component.empty()
                .append(ui.text("Identity type must be "))
                .append(ui.identityBadge(IdentityType.ONLINE))
                .append(ui.text(" or "))
                .append(ui.identityBadge(IdentityType.OFFLINE))
                .append(ui.text(": "))
                .append(Component.text(Objects.requireNonNull(value, "value"), MonbanPalette.WARNING))
                .append(ui.text(".")));
    }

    public Component invalidPage(String value) {
        return ui.error(Component.empty()
                .append(ui.text("Invalid page: "))
                .append(Component.text(Objects.requireNonNull(value, "value"), MonbanPalette.WARNING))
                .append(ui.text(". Page must be at least 1.")));
    }

    public Component invalidUsage(String action) {
        return ui.error(Component.empty()
                .append(ui.text("Invalid whitelist command usage for "))
                .append(Component.text(Objects.requireNonNull(action, "action"), MonbanPalette.WARNING))
                .append(ui.text(". Use "))
                .append(ui.command("/monban whitelist"))
                .append(ui.text(" to view usage.")));
    }

    public Component onlineProfileNotFound() {
        return ui.error(ui.text("Online profile not found."));
    }

    public Component onlineProfileUnavailable() {
        return ui.error(ui.text("Online profile lookup is temporarily unavailable. Try again later."));
    }

    public Component validationFailure(String message) {
        return ui.error(ui.text(Objects.requireNonNull(message, "message")));
    }

    public Component mutationFailure(LogTarget target) {
        return ui.error(ui.text(
                "Failed to update the monban whitelist. Check the "
                        + Objects.requireNonNull(target, "target").label + " log."
        ));
    }

    public Component readFailure(LogTarget target) {
        return ui.error(ui.text(
                "Failed to read the monban whitelist. Check the "
                        + Objects.requireNonNull(target, "target").label + " log."
        ));
    }

    public Component enabled() {
        return ui.success(ui.text("The monban whitelist is now enabled."));
    }

    public Component disabled() {
        return ui.success(ui.text("The monban whitelist is now disabled."));
    }

    public Component alreadyEnabled() {
        return ui.info(ui.text("The monban whitelist is already enabled."));
    }

    public Component alreadyDisabled() {
        return ui.info(ui.text("The monban whitelist is already disabled."));
    }

    public Component policyUpdateFailure(LogTarget target) {
        return ui.error(ui.text(
                "Failed to update the whitelist policy. Check the "
                        + Objects.requireNonNull(target, "target").label + " log."
        ));
    }

    public Component added(PlayerIdentity identity) {
        return ui.success(Component.empty()
                .append(ui.text("Added "))
                .append(ui.identity(identity))
                .append(ui.text(" to the monban whitelist.")));
    }

    public Component alreadyExists() {
        return ui.warning(ui.text("Whitelist entry already exists."));
    }

    public Component removed(PlayerIdentity identity) {
        return ui.success(Component.empty()
                .append(ui.text("Removed "))
                .append(ui.identity(identity))
                .append(ui.text(" from the monban whitelist.")));
    }

    public Component notFound() {
        return ui.info(ui.text("No matching whitelist entry exists."));
    }

    public WhitelistListView listing(List<AccessGrant> grants, IdentityType filter, int page) {
        Objects.requireNonNull(grants, "grants");
        if (page < 1) {
            return new WhitelistListView(false, List.of(invalidPage(Integer.toString(page))));
        }

        List<AccessGrant> sorted = grants.stream()
                .filter(grant -> filter == null || grant.identity().type() == filter)
                .sorted(WHITELIST_ORDER)
                .toList();

        int total = sorted.size();
        int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > totalPages) {
            Component error = ui.error(Component.empty()
                    .append(ui.text("Page "))
                    .append(Component.text(Integer.toString(page), MonbanPalette.WARNING))
                    .append(ui.text(" is out of range. Available pages: 1-" + totalPages + ".")));
            Component openLast = Component.empty()
                    .append(ui.muted("  "))
                    .append(ui.pageButton("Go to page " + totalPages, listCommand(filter, totalPages), true));
            return new WhitelistListView(false, List.of(error, openLast));
        }

        if (sorted.isEmpty()) {
            Component empty = filter == null
                    ? ui.info(ui.text("No whitelist entries found."))
                    : ui.info(Component.empty()
                            .append(ui.text("No "))
                            .append(ui.identityBadge(filter))
                            .append(ui.text(" whitelist entries found.")));
            return new WhitelistListView(true, List.of(empty));
        }

        int fromIndex = (page - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);
        List<Component> lines = new ArrayList<>(PAGE_SIZE + 3);
        lines.add(listHeader(filter, page, totalPages));
        for (int index = fromIndex; index < toIndex; index++) {
            lines.add(Component.empty()
                    .append(Component.text("  • ", MonbanPalette.SUBTLE))
                    .append(ui.identity(sorted.get(index).identity())));
        }
        lines.add(listFooter(filter, page, totalPages, fromIndex + 1, toIndex, total));
        return new WhitelistListView(true, lines);
    }

    public String listCommand(IdentityType filter, int page) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        return filter == null
                ? "/monban whitelist list " + page
                : "/monban whitelist list " + filter.name().toLowerCase(Locale.ROOT) + " " + page;
    }

    private Component listHeader(IdentityType filter, int page, int totalPages) {
        Component result = Component.empty().append(ui.title("whitelist"));
        if (filter != null) {
            result = result
                    .append(Component.text("  ", MonbanPalette.SUBTLE))
                    .append(ui.identityBadge(filter));
        }
        return result
                .append(Component.text("  ·  ", MonbanPalette.SUBTLE))
                .append(Component.text("page " + page + "/" + totalPages, MonbanPalette.MUTED));
    }

    private Component listFooter(
            IdentityType filter,
            int page,
            int totalPages,
            int first,
            int last,
            int total
    ) {
        Component previous = ui.pageButton(
                "‹ prev",
                listCommand(filter, Math.max(1, page - 1)),
                page > 1
        );
        Component next = ui.pageButton(
                "next ›",
                listCommand(filter, Math.min(totalPages, page + 1)),
                page < totalPages
        );
        return Component.empty()
                .append(Component.text("  " + first + "–" + last + " of " + total, MonbanPalette.MUTED))
                .append(Component.text("  ·  ", MonbanPalette.SUBTLE))
                .append(previous)
                .append(Component.text("  ", MonbanPalette.SUBTLE))
                .append(Component.text(page + "/" + totalPages, MonbanPalette.TEXT).decorate(TextDecoration.BOLD))
                .append(Component.text("  ", MonbanPalette.SUBTLE))
                .append(next);
    }

    private Component usageLine(String label, String command) {
        return Component.empty()
                .append(Component.text("  " + label, MonbanPalette.MUTED))
                .append(Component.text("  ", MonbanPalette.SUBTLE))
                .append(ui.command(command));
    }

    public enum LogTarget {
        SERVER("server"),
        PROXY("proxy");

        private final String label;

        LogTarget(String label) {
            this.label = label;
        }
    }
}
