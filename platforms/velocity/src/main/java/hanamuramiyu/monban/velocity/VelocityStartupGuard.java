package hanamuramiyu.monban.velocity;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import net.kyori.adventure.text.Component;

public final class VelocityStartupGuard {
    private static final Component NOT_READY_MESSAGE = Component.text(
            "monban is not ready to verify access. Please try again later."
    );

    @Subscribe(priority = Short.MIN_VALUE, async = false)
    public void onLogin(LoginEvent event) {
        event.setResult(ResultedEvent.ComponentResult.denied(NOT_READY_MESSAGE));
    }
}
