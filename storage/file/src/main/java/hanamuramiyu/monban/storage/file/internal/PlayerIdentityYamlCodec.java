package hanamuramiyu.monban.storage.file.internal;

import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerIdentityYamlCodec {
    private static final String TYPE_KEY = "type";
    private static final String NAME_KEY = "name";
    private static final String VERIFIED_UUID_KEY = "verified-uuid";
    private static final String TECHNICAL_UUID_KEY = "technical-uuid";

    private static final Set<String> ENTRY_FIELDS = Set.of(
            TYPE_KEY,
            NAME_KEY,
            VERIFIED_UUID_KEY,
            TECHNICAL_UUID_KEY
    );
    private static final Set<String> ONLINE_FIELDS = Set.of(TYPE_KEY, NAME_KEY, VERIFIED_UUID_KEY);
    private static final Set<String> OFFLINE_FIELDS = Set.of(TYPE_KEY, NAME_KEY, TECHNICAL_UUID_KEY);

    public PlayerIdentity decode(Map<?, ?> value, String path) throws IOException {
        requireOnlyFields(value, path, ENTRY_FIELDS);
        String typeValue = requireString(value.get(TYPE_KEY), path + "." + TYPE_KEY);

        IdentityType type;
        try {
            type = IdentityType.valueOf(typeValue);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid identity type at " + path + ": " + typeValue, exception);
        }

        requireOnlyFields(value, path, type == IdentityType.ONLINE ? ONLINE_FIELDS : OFFLINE_FIELDS);
        String name = requireString(value.get(NAME_KEY), path + "." + NAME_KEY);

        try {
            return switch (type) {
                case ONLINE -> {
                    UUID verifiedUuid = requireUuid(
                            value.get(VERIFIED_UUID_KEY),
                            path + "." + VERIFIED_UUID_KEY
                    );
                    yield PlayerIdentity.online(name, verifiedUuid);
                }
                case OFFLINE -> {
                    Object technicalUuidValue = value.get(TECHNICAL_UUID_KEY);
                    if (technicalUuidValue == null) {
                        yield PlayerIdentity.offline(name);
                    }
                    yield PlayerIdentity.offline(
                            name,
                            requireUuid(technicalUuidValue, path + "." + TECHNICAL_UUID_KEY)
                    );
                }
            };
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IOException("Invalid player identity at " + path, exception);
        }
    }

    public Map<String, Object> encode(PlayerIdentity identity) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put(TYPE_KEY, identity.type().name());
        serialized.put(NAME_KEY, identity.name());

        if (identity.type() == IdentityType.ONLINE) {
            serialized.put(VERIFIED_UUID_KEY, identity.verifiedUuid().orElseThrow().toString());
        } else {
            identity.technicalUuid().ifPresent(uuid -> serialized.put(TECHNICAL_UUID_KEY, uuid.toString()));
        }

        return serialized;
    }

    private static String requireString(Object value, String path) throws IOException {
        if (value instanceof String string) {
            return string;
        }
        throw new IOException("Expected string at " + path);
    }

    private static UUID requireUuid(Object value, String path) throws IOException {
        String uuidValue = requireString(value, path);
        try {
            return UUID.fromString(uuidValue);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid UUID at " + path + ": " + uuidValue, exception);
        }
    }

    private static void requireOnlyFields(Map<?, ?> map, String path, Set<String> allowedFields) throws IOException {
        for (Object key : map.keySet()) {
            if (!(key instanceof String field)) {
                throw new IOException("Expected string field name at " + path);
            }
            if (!allowedFields.contains(field)) {
                throw new IOException("Unknown field at " + path + "." + field);
            }
        }
    }
}
