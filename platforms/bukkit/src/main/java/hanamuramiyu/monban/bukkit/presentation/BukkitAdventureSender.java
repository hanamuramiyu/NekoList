package hanamuramiyu.monban.bukkit.presentation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.Objects;

public final class BukkitAdventureSender {
    private static final GsonComponentSerializer SERIALIZER = GsonComponentSerializer.gson();

    private BukkitAdventureSender() {
    }

    public static void send(CommandSender sender, Component component) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(component, "component");
        sender.spigot().sendMessage(ComponentSerializer.parse(SERIALIZER.serialize(component)));
    }
}
