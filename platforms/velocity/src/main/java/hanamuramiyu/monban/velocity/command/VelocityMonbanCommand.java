package hanamuramiyu.monban.velocity.command;

import com.velocitypowered.api.command.BrigadierCommand;

import java.util.Objects;

public final class VelocityMonbanCommand {
    private VelocityMonbanCommand() {
    }

    public static BrigadierCommand create(
            VelocityWhitelistCommand whitelistCommand,
            VelocityAccessCommand accessCommand,
            VelocityStatusCommand statusCommand
    ) {
        Objects.requireNonNull(whitelistCommand, "whitelistCommand");
        Objects.requireNonNull(accessCommand, "accessCommand");
        Objects.requireNonNull(statusCommand, "statusCommand");

        var root = BrigadierCommand.literalArgumentBuilder("monban");
        root.then(whitelistCommand.build());
        root.then(accessCommand.build());
        root.then(statusCommand.build());
        return new BrigadierCommand(root);
    }
}
