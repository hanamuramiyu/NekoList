package hanamuramiyu.monban.presentation;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;

public record WhitelistListView(boolean successful, List<Component> lines) {
    public WhitelistListView {
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
    }
}
