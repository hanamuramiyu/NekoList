package hanamuramiyu.monban.paper.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import hanamuramiyu.monban.presentation.HelpPresentation;

import java.util.Objects;

public final class PaperMonbanCommand {
    private PaperMonbanCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(PaperWhitelistCommand whitelistCommand) {
        Objects.requireNonNull(whitelistCommand, "whitelistCommand");
        HelpPresentation help = new HelpPresentation();
        return Commands.literal("monban")
                .executes(context -> {
                    help.lines(context.getSource().getSender().hasPermission(PaperWhitelistCommand.PERMISSION), false, false, false)
                            .forEach(context.getSource().getSender()::sendMessage);
                    return Command.SINGLE_SUCCESS;
                })
                .then(whitelistCommand.build())
                .build();
    }
}
