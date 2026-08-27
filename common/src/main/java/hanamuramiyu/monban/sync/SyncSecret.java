package hanamuramiyu.monban.sync;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

public final class SyncSecret {
    private static final int MIN_LENGTH = 16;
    private static final int MAX_LENGTH = 128;

    private final byte[] value;

    private SyncSecret(byte[] value) {
        this.value = value;
    }

    public static SyncSecret of(byte[] value) {
        Objects.requireNonNull(value, "value");
        if (value.length < MIN_LENGTH || value.length > MAX_LENGTH) {
            throw new IllegalArgumentException("Sync secret length must be between 16 and 128 bytes.");
        }
        return new SyncSecret(value.clone());
    }

    public static SyncSecret fromBase64(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return of(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Sync secret must be valid Base64.", exception);
        }
    }

    public byte[] bytes() {
        return value.clone();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof SyncSecret other && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }
}
