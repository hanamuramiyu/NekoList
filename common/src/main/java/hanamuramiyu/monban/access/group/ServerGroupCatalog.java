package hanamuramiyu.monban.access.group;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ServerGroupCatalog {
    private final List<ServerGroupDefinition> groups;
    private final Map<String, ServerGroupDefinition> groupsById;
    private final Map<String, ServerGroupDefinition> groupsByServer;

    public ServerGroupCatalog(List<ServerGroupDefinition> groups) {
        Objects.requireNonNull(groups, "groups");

        List<ServerGroupDefinition> snapshot = List.copyOf(groups);
        Map<String, ServerGroupDefinition> byId = new LinkedHashMap<>();
        Map<String, ServerGroupDefinition> byServer = new LinkedHashMap<>();

        for (ServerGroupDefinition group : snapshot) {
            Objects.requireNonNull(group, "group");

            ServerGroupDefinition duplicateId = byId.putIfAbsent(group.id(), group);
            if (duplicateId != null) {
                throw new IllegalArgumentException("Duplicate server group id: " + group.id());
            }

            for (String server : group.servers()) {
                ServerGroupDefinition existing = byServer.putIfAbsent(server, group);
                if (existing != null) {
                    throw new IllegalArgumentException(
                            "Server " + server + " belongs to multiple groups: "
                                    + existing.id() + " and " + group.id()
                    );
                }
            }
        }

        this.groups = snapshot;
        this.groupsById = Map.copyOf(byId);
        this.groupsByServer = Map.copyOf(byServer);
    }

    public Optional<ServerGroupDefinition> findById(String id) {
        return Optional.ofNullable(groupsById.get(requireLookupValue(id, "id")));
    }

    public Optional<ServerGroupDefinition> findForServer(String serverName) {
        return Optional.ofNullable(groupsByServer.get(requireLookupValue(serverName, "serverName")));
    }

    public List<ServerGroupDefinition> findAll() {
        return groups;
    }

    private static String requireLookupValue(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not have leading or trailing whitespace: " + value);
        }
        return value;
    }
}
