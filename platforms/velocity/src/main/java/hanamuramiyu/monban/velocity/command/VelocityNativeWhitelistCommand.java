package hanamuramiyu.monban.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import hanamuramiyu.monban.presentation.MonbanUi;
import hanamuramiyu.monban.presentation.WhitelistPresentation;

import java.util.List;
import java.util.Objects;

public final class VelocityNativeWhitelistCommand implements SimpleCommand {
    private final WhitelistPresentation presentation = new WhitelistPresentation();

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission(VelocityWhitelistCommand.PERMISSION)) {
            invocation.source().sendMessage(new MonbanUi().unknownCommand());
            return;
        }
        presentation.usage().forEach(invocation.source()::sendMessage);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        if (!invocation.source().hasPermission(VelocityWhitelistCommand.PERMISSION)) {
            return List.of();
        }
        return List.of("add", "remove", "list");
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}
