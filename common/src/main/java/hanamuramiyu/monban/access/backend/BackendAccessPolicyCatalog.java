package hanamuramiyu.monban.access.backend;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BackendAccessPolicyCatalog {
    private final BackendAccessMode defaultMode;
    private final Map<String, BackendAccessMode> serverGroupPolicies;
    private final Map<String, BackendAccessMode> serverPolicies;

    public BackendAccessPolicyCatalog(
            BackendAccessMode defaultMode,
            Map<String, BackendAccessMode> serverGroupPolicies,
            Map<String, BackendAccessMode> serverPolicies
    ) {
        this.defaultMode = Objects.requireNonNull(defaultMode, "defaultMode");
        this.serverGroupPolicies = copyPolicies(serverGroupPolicies, "serverGroupPolicies");
        this.serverPolicies = copyPolicies(serverPolicies, "serverPolicies");
    }

    public BackendAccessMode defaultMode() {
        return defaultMode;
    }

    public Map<String, BackendAccessMode> serverGroupPolicies() {
        return serverGroupPolicies;
    }

    public Map<String, BackendAccessMode> serverPolicies() {
        return serverPolicies;
    }

    public Optional<BackendAccessMode> findServerGroupPolicy(String groupId) {
        return Optional.ofNullable(serverGroupPolicies.get(requireKey(groupId, "groupId")));
    }

    public Optional<BackendAccessMode> findServerPolicy(String serverName) {
        return Optional.ofNullable(serverPolicies.get(requireKey(serverName, "serverName")));
    }

    public BackendAccessMode effectiveMode(BackendTarget target) {
        Objects.requireNonNull(target, "target");
        BackendAccessMode serverMode = serverPolicies.get(target.serverName());
        if (serverMode != null) {
            return serverMode;
        }
        if (target.serverGroupId().isPresent()) {
            BackendAccessMode groupMode = serverGroupPolicies.get(target.serverGroupId().orElseThrow());
            if (groupMode != null) {
                return groupMode;
            }
        }
        return defaultMode;
    }

    public int explicitPolicyCount() {
        return serverGroupPolicies.size() + serverPolicies.size();
    }

    private static Map<String, BackendAccessMode> copyPolicies(Map<String, BackendAccessMode> source, String field) {
        Objects.requireNonNull(source, field);
        Map<String, BackendAccessMode> copy = new LinkedHashMap<>();
        for (Map.Entry<String, BackendAccessMode> entry : source.entrySet()) {
            String key = requireKey(entry.getKey(), field + " key");
            BackendAccessMode mode = Objects.requireNonNull(entry.getValue(), field + " value");
            copy.put(key, mode);
        }
        return Map.copyOf(copy);
    }

    private static String requireKey(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank.");
        if (!value.equals(value.strip())) throw new IllegalArgumentException(field + " must not have leading or trailing whitespace: " + value);
        return value;
    }
}
