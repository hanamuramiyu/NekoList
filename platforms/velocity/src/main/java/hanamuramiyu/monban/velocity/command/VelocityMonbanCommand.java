package hanamuramiyu.monban.velocity.command;

import com.velocitypowered.api.command.BrigadierCommand;
import hanamuramiyu.monban.presentation.HelpPresentation;

import java.util.Objects;

public final class VelocityMonbanCommand {
    private VelocityMonbanCommand() {
    }

    public static BrigadierCommand create(
            VelocityWhitelistCommand whitelistCommand,
            VelocityAccessCommand accessCommand,
            VelocityStatusCommand statusCommand
    ) {
        return create(whitelistCommand, null, accessCommand, statusCommand);
    }

    public static BrigadierCommand create(
            VelocityWhitelistCommand whitelistCommand,
            VelocityLookupCommand lookupCommand,
            VelocityAccessCommand accessCommand,
            VelocityStatusCommand statusCommand
    ) {
        Objects.requireNonNull(whitelistCommand, "whitelistCommand");
        Objects.requireNonNull(accessCommand, "accessCommand");
        Objects.requireNonNull(statusCommand, "statusCommand");

        var root = BrigadierCommand.literalArgumentBuilder("monban");
        HelpPresentation help = new HelpPresentation();
        root.executes(context -> {
                    help.lines(
                            context.getSource().hasPermission(VelocityWhitelistCommand.PERMISSION),
                            lookupCommand != null && context.getSource().hasPermission(VelocityLookupCommand.PERMISSION),
                            context.getSource().hasPermission(VelocityAccessCommand.PERMISSION),
                            context.getSource().hasPermission(VelocityStatusCommand.PERMISSION)
                    )
                    .forEach(context.getSource()::sendMessage);
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        });
        root.then(whitelistCommand.build());
        if (lookupCommand != null) {
            root.then(lookupCommand.build());
        }
        root.then(accessCommand.build());
        root.then(statusCommand.build());
        return new BrigadierCommand(root);
    }
}
