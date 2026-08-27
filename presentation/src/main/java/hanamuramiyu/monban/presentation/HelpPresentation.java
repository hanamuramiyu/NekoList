package hanamuramiyu.monban.presentation;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

public final class HelpPresentation {
    private final MonbanUi ui;

    public HelpPresentation() {
        this(new MonbanUi());
    }

    public HelpPresentation(MonbanUi ui) {
        this.ui = ui;
    }

    public List<Component> lines(boolean whitelist, boolean lookup, boolean access, boolean status) {
        return lines(whitelist, lookup, access, status, false);
    }

    public List<Component> lines(
            boolean whitelist,
            boolean lookup,
            boolean access,
            boolean status,
            boolean group
    ) {
        List<Component> lines = new ArrayList<>();
        lines.add(ui.title("3.2.0"));
        lines.add(ui.muted("────────────────────────────"));
        if (whitelist) {
            lines.add(entry("/monban whitelist", "Manage network whitelist"));
        }
        if (lookup) {
            lines.add(entry("/monban lookup <player>", "Inspect player access"));
        }
        if (access) {
            lines.add(entry("/monban access", "Manage scoped access"));
        }
        if (status) {
            lines.add(entry("/monban status", "View access status"));
        }
        if (group) {
            lines.add(entry("/monban group", "Manage player groups and permissions"));
        }
        if (lines.size() == 2) {
            lines.add(ui.unknownCommand());
        }
        return List.copyOf(lines);
    }

    private Component entry(String command, String description) {
        return Component.empty().append(ui.command(command)).append(ui.text("  " + description));
    }
}
