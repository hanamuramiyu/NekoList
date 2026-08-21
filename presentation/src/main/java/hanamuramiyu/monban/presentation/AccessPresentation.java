package hanamuramiyu.monban.presentation;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class AccessPresentation {
    public static final int PAGE_SIZE = 10;

    private static final Comparator<AccessGrant> ORDER = Comparator
            .comparing((AccessGrant grant) -> grant.identity().normalizedName())
            .thenComparing(grant -> grant.identity().type().name())
            .thenComparing(grant -> grant.scope().type().name())
            .thenComparing(grant -> grant.scope().id().orElse(""))
            .thenComparing(grant -> grant.identity().verifiedUuid().map(UUID::toString).orElse(""));

    private final MonbanUi ui;

    public AccessPresentation() {
        this(new MonbanUi());
    }

    public AccessPresentation(MonbanUi ui) {
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    public Component permissionDenied() {
        return ui.error(Component.empty()
                .append(ui.text("You do not have permission to use "))
                .append(ui.command("/monban access"))
                .append(ui.text(".")));
    }

    public List<Component> usage() {
        return List.of(
                ui.title("access"),
                ui.muted("────────────────────────────"),
                entry("Grant network", "/monban access grant network offline <name>"),
                entry("Grant network", "/monban access grant network online <name>"),
                entry("Grant network", "/monban access grant network online <name> <uuid>"),
                entry("Grant group", "/monban access grant group <group-id> offline <name>"),
                entry("Grant group", "/monban access grant group <group-id> online <name>"),
                entry("Grant server", "/monban access grant server <server-name> offline <name>"),
                entry("Grant server", "/monban access grant server <server-name> online <name>"),
                entry("Revoke", "/monban access revoke <network|group|server> ..."),
                entry("List", "/monban access list [page]"),
                entry("List filtered", "/monban access list <network|group|server> ...")
        );
    }

    public Component mutationFailure() {
        return ui.error(ui.text("Failed to update access grants. Check the proxy log."));
    }

    public Component readFailure() {
        return ui.error(ui.text("Failed to read access grants. Check the proxy log."));
    }

    public Component invalidInput(String message) {
        return ui.error(ui.text(Objects.requireNonNull(message, "message")));
    }

    public Component added(AccessScope scope, hanamuramiyu.monban.identity.PlayerIdentity identity) {
        return ui.success(Component.empty()
                .append(ui.text("Granted "))
                .append(ui.scopeText(scope))
                .append(ui.text(" access to "))
                .append(ui.fullIdentity(identity))
                .append(ui.text(".")));
    }

    public Component alreadyExists() {
        return ui.warning(ui.text("Access grant already exists."));
    }

    public Component removed(AccessScope scope, hanamuramiyu.monban.identity.PlayerIdentity identity) {
        return ui.success(Component.empty()
                .append(ui.text("Revoked "))
                .append(ui.scopeText(scope))
                .append(ui.text(" access from "))
                .append(ui.fullIdentity(identity))
                .append(ui.text(".")));
    }

    public Component notFound() {
        return ui.info(ui.text("No matching access grant exists."));
    }

    public AccessListView listing(List<AccessGrant> grants, AccessScope filter, int page) {
        Objects.requireNonNull(grants, "grants");
        if (page < 1) {
            return new AccessListView(false, List.of(invalidPage(Integer.toString(page))));
        }

        List<AccessGrant> sorted = grants.stream()
                .filter(grant -> filter == null || grant.scope().equals(filter))
                .sorted(ORDER)
                .toList();
        int total = sorted.size();
        int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > totalPages) {
            return new AccessListView(false, List.of(
                    ui.error(ui.text("Page " + page + " is out of range. Available pages: 1-" + totalPages + ".")),
                    ui.pageButton("Go to page " + totalPages, listCommand(filter, totalPages), true)
            ));
        }
        if (sorted.isEmpty()) {
            return new AccessListView(true, List.of(ui.info(filter == null
                    ? ui.text("No access grants found.")
                    : ui.text("No access grants found for " + filter + "."))));
        }

        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);
        List<Component> lines = new ArrayList<>(to - from + 3);
        lines.add(header(filter, page, totalPages, total));
        for (int index = from; index < to; index++) {
            AccessGrant grant = sorted.get(index);
            Component line = Component.empty().append(Component.text("  " + (index + 1) + ". ", MonbanPalette.SUBTLE));
            if (filter == null) {
                line = line.append(ui.scopeText(grant.scope()))
                        .append(Component.text(" — ", MonbanPalette.SUBTLE));
            }
            line = line.append(ui.fullIdentity(grant.identity()));
            lines.add(line);
        }
        lines.add(footer(filter, page, totalPages, from + 1, to, total));
        return new AccessListView(true, lines);
    }

    public String listCommand(AccessScope filter, int page) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (filter == null) {
            return "/monban access list " + page;
        }
        return switch (filter.type()) {
            case NETWORK -> "/monban access list network " + page;
            case SERVER_GROUP -> "/monban access list group " + filter.id().orElseThrow() + " " + page;
            case SERVER -> "/monban access list server " + quoteIfRequired(filter.id().orElseThrow()) + " " + page;
        };
    }

    private Component header(AccessScope filter, int page, int totalPages, int total) {
        String prefix = filter == null ? "Access grants" : "Access grants for " + filter;
        return ui.title(prefix + " — page " + page + "/" + totalPages + " — " + total + " total");
    }

    private Component footer(AccessScope filter, int page, int totalPages, int first, int last, int total) {
        return Component.empty()
                .append(Component.text("  " + first + "–" + last + " of " + total, MonbanPalette.MUTED))
                .append(Component.text("  ", MonbanPalette.SUBTLE))
                .append(ui.pageButton("‹", listCommand(filter, Math.max(1, page - 1)), page > 1))
                .append(Component.text("  ", MonbanPalette.SUBTLE))
                .append(Component.text(page + "/" + totalPages, MonbanPalette.TEXT).decorate(TextDecoration.BOLD))
                .append(Component.text("  ", MonbanPalette.SUBTLE))
                .append(ui.pageButton("›", listCommand(filter, Math.min(totalPages, page + 1)), page < totalPages));
    }

    private Component invalidPage(String value) {
        return ui.error(ui.text("Invalid page: " + value + ". Page must be at least 1."));
    }

    private Component entry(String label, String command) {
        return Component.empty()
                .append(ui.command(command))
                .append(ui.text("  " + label));
    }

    private static String quoteIfRequired(String value) {
        return value.matches("[A-Za-z0-9_.-]+") ? value : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
