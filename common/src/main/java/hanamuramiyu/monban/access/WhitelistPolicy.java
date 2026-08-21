package hanamuramiyu.monban.access;

import java.util.concurrent.atomic.AtomicBoolean;

public final class WhitelistPolicy {
    private final AtomicBoolean enabled;

    public WhitelistPolicy(boolean enabled) {
        this.enabled = new AtomicBoolean(enabled);
    }

    public boolean enabled() {
        return enabled.get();
    }

    public boolean setEnabled(boolean value) {
        return enabled.getAndSet(value);
    }
}
