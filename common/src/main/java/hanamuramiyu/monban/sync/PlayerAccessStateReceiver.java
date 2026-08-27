package hanamuramiyu.monban.sync;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class PlayerAccessStateReceiver {
    private final PlayerAccessStateCodec codec;
    private final SyncSecret secret;
    private final AtomicReference<PlayerAccessStateSnapshot> current = new AtomicReference<>();

    public PlayerAccessStateReceiver(PlayerAccessStateCodec codec, SyncSecret secret) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.secret = Objects.requireNonNull(secret, "secret");
    }

    public boolean accept(byte[] payload) {
        PlayerAccessStateSnapshot received = codec.decode(payload, secret);
        while (true) {
            PlayerAccessStateSnapshot previous = current.get();
            if (previous != null && received.revision() <= previous.revision()) {
                return false;
            }
            if (current.compareAndSet(previous, received)) {
                return true;
            }
        }
    }

    public Optional<PlayerAccessStateSnapshot> current() {
        return Optional.ofNullable(current.get());
    }
}
