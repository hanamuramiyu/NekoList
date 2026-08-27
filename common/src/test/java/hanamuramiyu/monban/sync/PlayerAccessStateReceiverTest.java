package hanamuramiyu.monban.sync;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAccessStateReceiverTest {
    private static final SyncSecret SECRET = SyncSecret.fromBase64(
            Base64.getEncoder().encodeToString("monban-sync-secret".getBytes(StandardCharsets.UTF_8))
    );
    private final PlayerAccessStateCodec codec = new PlayerAccessStateCodec();

    @Test
    void acceptsOnlyNewerRevisions() {
        PlayerAccessStateReceiver receiver = new PlayerAccessStateReceiver(codec, SECRET);
        byte[] first = codec.encode(snapshot(1), SECRET);
        byte[] second = codec.encode(snapshot(2), SECRET);

        assertTrue(receiver.accept(first));
        assertFalse(receiver.accept(first));
        assertTrue(receiver.accept(second));
        assertEquals(2, receiver.current().orElseThrow().revision());
    }

    private static PlayerAccessStateSnapshot snapshot(long revision) {
        return new PlayerAccessStateSnapshot(revision, List.of(), List.of(), List.of(), List.of());
    }
}
