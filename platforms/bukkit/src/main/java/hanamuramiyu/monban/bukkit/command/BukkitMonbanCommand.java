package hanamuramiyu.monban.bukkit.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import hanamuramiyu.monban.bukkit.presentation.BukkitAdventureSender;
import hanamuramiyu.monban.presentation.HelpPresentation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class BukkitMonbanCommand implements CommandExecutor, TabCompleter {
    private final BukkitWhitelistCommand whitelistCommand;
    private final boolean backendManaged;
    private final HelpPresentation help = new HelpPresentation();

    public BukkitMonbanCommand(BukkitWhitelistCommand whitelistCommand) {
        this(whitelistCommand, false);
    }

    public BukkitMonbanCommand(BukkitWhitelistCommand whitelistCommand, boolean backendManaged) {
        this.whitelistCommand = backendManaged ? null : Objects.requireNonNull(whitelistCommand, "whitelistCommand");
        this.backendManaged = backendManaged;
    }

    public BukkitMonbanCommand(boolean backendManaged) {
        this(null, backendManaged);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (backendManaged) {
            sender.sendMessage("Whitelist administration is controlled by monban on Velocity.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("whitelist")) {
            return whitelistCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        help.lines(sender.hasPermission(BukkitWhitelistCommand.PERMISSION), false, false, false)
                .forEach(component -> BukkitAdventureSender.send(sender, component));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (backendManaged) {
            return List.of();
        }
        if (args.length == 1) {
            return sender.hasPermission(BukkitWhitelistCommand.PERMISSION)
                    && "whitelist".startsWith(args[0].toLowerCase())
                    ? List.of("whitelist")
                    : List.of();
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("whitelist")) {
            return whitelistCommand.suggestions(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }
}
