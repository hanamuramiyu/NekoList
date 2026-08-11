package hanamuramiyu.monban.paper.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.util.Objects;

public final class PaperMonbanCommand {
    private PaperMonbanCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(PaperWhitelistCommand whitelistCommand) {
        Objects.requireNonNull(whitelistCommand, "whitelistCommand");
        return Commands.literal("monban")
                .then(whitelistCommand.build())
                .build();
    }
}
