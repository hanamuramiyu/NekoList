package hanamuramiyu.monban.presentation;

import net.kyori.adventure.text.Component;

import java.util.List;

public record AccessListView(boolean successful, List<Component> lines) {
    public AccessListView {
        lines = List.copyOf(lines);
    }
}
