package hanamuramiyu.monban.sync;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.access.scope.AccessScopeType;
import hanamuramiyu.monban.identity.IdentityType;
import hanamuramiyu.monban.identity.PlayerIdentity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerAccessStateCodec {
    private static final int MAGIC = 0x4D425354;
    private static final int VERSION = 2;
    private static final int MAX_PAYLOAD_SIZE = 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;
    private static final int SIGNATURE_SIZE = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public byte[] encode(PlayerAccessStateSnapshot snapshot, SyncSecret secret) {
        try {
            ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
            DataOutputStream body = new DataOutputStream(bodyBytes);
            body.writeInt(MAGIC);
            body.writeInt(VERSION);
            body.writeLong(snapshot.revision());
            body.writeBoolean(snapshot.networkWhitelistEnabled());
            writeAccessGrants(body, snapshot.accessGrants());
            writeGroups(body, snapshot.groups());
            writeAssignments(body, snapshot.assignments());
            writePermissions(body, snapshot.permissions());
            body.flush();

            byte[] content = bodyBytes.toByteArray();
            byte[] signature = sign(content, secret);
            ByteArrayOutputStream resultBytes = new ByteArrayOutputStream(content.length + SIGNATURE_SIZE);
            resultBytes.write(content);
            resultBytes.write(signature);
            byte[] result = resultBytes.toByteArray();
            if (result.length > MAX_PAYLOAD_SIZE) {
                throw new IllegalArgumentException("State snapshot exceeds the maximum payload size.");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode state snapshot.", exception);
        }
    }

    public PlayerAccessStateSnapshot decode(byte[] payload, SyncSecret secret) {
        if (payload == null || payload.length < SIGNATURE_SIZE || payload.length > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Invalid state snapshot payload size.");
        }
        int contentLength = payload.length - SIGNATURE_SIZE;
        byte[] content = java.util.Arrays.copyOf(payload, contentLength);
        byte[] signature = java.util.Arrays.copyOfRange(payload, contentLength, payload.length);
        if (!MessageDigest.isEqual(signature, sign(content, secret))) {
            throw new IllegalArgumentException("Invalid state snapshot signature.");
        }

        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(content));
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IllegalArgumentException("Unsupported state snapshot protocol.");
            }
            long revision = input.readLong();
            boolean networkWhitelistEnabled = input.readBoolean();
            List<AccessGrant> accessGrants = readAccessGrants(input);
            List<PlayerGroupDefinition> groups = readGroups(input);
            List<PlayerGroupAssignment> assignments = readAssignments(input);
            List<PlayerPermissionGrant> permissions = readPermissions(input);
            if (input.available() != 0) {
                throw new IllegalArgumentException("Trailing data in state snapshot.");
            }
            return new PlayerAccessStateSnapshot(
                    revision,
                    networkWhitelistEnabled,
                    accessGrants,
                    groups,
                    assignments,
                    permissions
            );
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Truncated state snapshot.", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Invalid state snapshot.", exception);
        }
    }

    private static void writeAccessGrants(DataOutputStream output, List<AccessGrant> grants) throws IOException {
        writeCount(output, grants.size());
        for (AccessGrant grant : grants) {
            writeScope(output, grant.scope());
            writeIdentity(output, grant.identity());
        }
    }

    private static List<AccessGrant> readAccessGrants(DataInputStream input) throws IOException {
        int count = readCount(input);
        List<AccessGrant> grants = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            grants.add(new AccessGrant(readScope(input), readIdentity(input)));
        }
        return grants;
    }

    private static void writeGroups(DataOutputStream output, List<PlayerGroupDefinition> groups) throws IOException {
        writeCount(output, groups.size());
        for (PlayerGroupDefinition group : groups) {
            writeString(output, group.id());
            writeCount(output, group.accessGrants().size());
            for (AccessScope scope : group.accessGrants()) {
                writeScope(output, scope);
            }
            writeCount(output, group.permissions().size());
            for (PermissionGrant permission : group.permissions()) {
                writeScope(output, permission.scope());
                writeString(output, permission.node());
            }
        }
    }

    private static List<PlayerGroupDefinition> readGroups(DataInputStream input) throws IOException {
        int count = readCount(input);
        List<PlayerGroupDefinition> groups = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String id = readString(input);
            int accessCount = readCount(input);
            List<AccessScope> accessGrants = readScopes(input, accessCount);
            int permissionCount = readCount(input);
            List<PermissionGrant> permissions = new ArrayList<>(permissionCount);
            for (int permissionIndex = 0; permissionIndex < permissionCount; permissionIndex++) {
                permissions.add(new PermissionGrant(readScope(input), readString(input)));
            }
            groups.add(new PlayerGroupDefinition(id, accessGrants, permissions));
        }
        return groups;
    }

    private static void writeAssignments(DataOutputStream output, List<PlayerGroupAssignment> assignments)
            throws IOException {
        writeCount(output, assignments.size());
        for (PlayerGroupAssignment assignment : assignments) {
            writeIdentity(output, assignment.identity());
            writeString(output, assignment.groupId());
        }
    }

    private static List<PlayerGroupAssignment> readAssignments(DataInputStream input) throws IOException {
        int count = readCount(input);
        List<PlayerGroupAssignment> assignments = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            assignments.add(new PlayerGroupAssignment(readIdentity(input), readString(input)));
        }
        return assignments;
    }

    private static void writePermissions(DataOutputStream output, List<PlayerPermissionGrant> permissions)
            throws IOException {
        writeCount(output, permissions.size());
        for (PlayerPermissionGrant permission : permissions) {
            writeIdentity(output, permission.identity());
            writeScope(output, permission.grant().scope());
            writeString(output, permission.grant().node());
        }
    }

    private static List<PlayerPermissionGrant> readPermissions(DataInputStream input) throws IOException {
        int count = readCount(input);
        List<PlayerPermissionGrant> permissions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            permissions.add(new PlayerPermissionGrant(
                    readIdentity(input),
                    new PermissionGrant(readScope(input), readString(input))
            ));
        }
        return permissions;
    }

    private static void writeScope(DataOutputStream output, AccessScope scope) throws IOException {
        output.writeByte(scope.type().ordinal());
        if (scope.type() != AccessScopeType.NETWORK) {
            writeString(output, scope.id().orElseThrow());
        }
    }

    private static AccessScope readScope(DataInputStream input) throws IOException {
        int type = input.readUnsignedByte();
        if (type < 0 || type >= AccessScopeType.values().length) {
            throw new IllegalArgumentException("Invalid access scope type.");
        }
        AccessScopeType scopeType = AccessScopeType.values()[type];
        return scopeType == AccessScopeType.NETWORK
                ? AccessScope.network()
                : scopeType == AccessScopeType.SERVER_GROUP
                ? AccessScope.serverGroup(readString(input))
                : AccessScope.server(readString(input));
    }

    private static void writeIdentity(DataOutputStream output, PlayerIdentity identity) throws IOException {
        output.writeByte(identity.type().ordinal());
        writeString(output, identity.name());
        output.writeBoolean(identity.technicalUuid().isPresent());
        if (identity.technicalUuid().isPresent()) {
            UUID uuid = identity.technicalUuid().orElseThrow();
            output.writeLong(uuid.getMostSignificantBits());
            output.writeLong(uuid.getLeastSignificantBits());
        }
    }

    private static PlayerIdentity readIdentity(DataInputStream input) throws IOException {
        int type = input.readUnsignedByte();
        if (type < 0 || type >= IdentityType.values().length) {
            throw new IllegalArgumentException("Invalid identity type.");
        }
        String name = readString(input);
        UUID uuid = null;
        if (input.readBoolean()) {
            uuid = new UUID(input.readLong(), input.readLong());
        }
        return IdentityType.values()[type] == IdentityType.ONLINE
                ? PlayerIdentity.online(name, uuid)
                : uuid == null ? PlayerIdentity.offline(name) : PlayerIdentity.offline(name, uuid);
    }

    private static List<AccessScope> readScopes(DataInputStream input, int count) throws IOException {
        List<AccessScope> scopes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            scopes.add(readScope(input));
        }
        return scopes;
    }

    private static void writeCount(DataOutputStream output, int count) throws IOException {
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Snapshot entry count exceeds the limit.");
        }
        output.writeInt(count);
    }

    private static int readCount(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid snapshot entry count.");
        }
        return count;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65535) {
            throw new IllegalArgumentException("Snapshot string is too long.");
        }
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] sign(byte[] content, SyncSecret secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.bytes(), HMAC_ALGORITHM));
            return mac.doFinal(content);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign state snapshot.", exception);
        }
    }
}
