package hanamuramiyu.monban.access.group;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ServerGroupDefinition(String id, List<String> servers) {
    public ServerGroupDefinition {
        id = requireCleanText(id, "id");
        Objects.requireNonNull(servers, "servers");

        List<String> copy = new ArrayList<>(servers.size());
        Set<String> uniqueServers = new HashSet<>();
        for (String server : servers) {
            String checkedServer = requireCleanText(server, "server");
            if (!uniqueServers.add(checkedServer)) {
                throw new IllegalArgumentException(
                        "Server group " + id + " contains duplicate server: " + checkedServer
                );
            }
            copy.add(checkedServer);
        }

        servers = List.copyOf(copy);
    }

    private static String requireCleanText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Server group " + field + " must not be blank.");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(
                    "Server group " + field + " must not have leading or trailing whitespace: " + value
            );
        }
        return value;
    }
}
