package hanamuramiyu.monban.bukkit.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class BukkitMonbanCommand implements CommandExecutor, TabCompleter {
    private final BukkitWhitelistCommand whitelistCommand;

    public BukkitMonbanCommand(BukkitWhitelistCommand whitelistCommand) {
        this.whitelistCommand = Objects.requireNonNull(whitelistCommand, "whitelistCommand");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length > 0 && args[0].equalsIgnoreCase("whitelist")) {
            return whitelistCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        if (sender.hasPermission(BukkitWhitelistCommand.PERMISSION)) {
            sender.sendMessage("Usage: /monban whitelist ...");
        } else {
            sender.sendMessage("No monban subcommands are available to you.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
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
