package hanamuramiyu.monban.access.effective;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantInventory;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.PlayerGroupRepository;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.sync.PlayerAccessStateSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PlayerAccessResolver {
    private final AccessGrantInventory directAccessGrants;
    private final PlayerGroupRepository groupRepository;
    private final PlayerGroupAssignmentRepository assignmentRepository;
    private final PlayerPermissionGrantRepository directPermissionGrants;

    public PlayerAccessResolver(
            AccessGrantInventory directAccessGrants,
            PlayerGroupRepository groupRepository,
            PlayerGroupAssignmentRepository assignmentRepository,
            PlayerPermissionGrantRepository directPermissionGrants
    ) {
        this.directAccessGrants = Objects.requireNonNull(directAccessGrants, "directAccessGrants");
        this.groupRepository = Objects.requireNonNull(groupRepository, "groupRepository");
        this.assignmentRepository = Objects.requireNonNull(assignmentRepository, "assignmentRepository");
        this.directPermissionGrants = Objects.requireNonNull(directPermissionGrants, "directPermissionGrants");
    }

    public PlayerAccessSnapshot resolve(PlayerIdentity identity) {
        Objects.requireNonNull(identity, "identity");

        List<PlayerGroupDefinition> groups = resolveGroups(identity);
        return resolve(
                identity,
                directAccessGrants.findAll().stream()
                        .filter(grant -> grant.identity().equals(identity))
                        .toList(),
                groups,
                directPermissionGrants.findAll(identity)
        );
    }

    public PlayerAccessSnapshot resolve(PlayerAccessStateSnapshot state, PlayerIdentity identity) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(identity, "identity");

        List<PlayerGroupDefinition> groups = resolveGroups(state, identity);
        return resolve(
                identity,
                state.accessGrants().stream()
                        .filter(grant -> matchesRemoteIdentity(grant.identity(), identity))
                        .toList(),
                groups,
                state.permissions().stream()
                        .filter(grant -> matchesRemoteIdentity(grant.identity(), identity))
                        .toList()
        );
    }

    private PlayerAccessSnapshot resolve(
            PlayerIdentity identity,
            List<AccessGrant> accessGrants,
            List<PlayerGroupDefinition> groups,
            List<PlayerPermissionGrant> permissions
    ) {
        return new PlayerAccessSnapshot(
                identity,
                groups,
                resolveAccess(accessGrants, groups),
                resolvePermissions(permissions, groups)
        );
    }

    private List<PlayerGroupDefinition> resolveGroups(PlayerIdentity identity) {
        List<PlayerGroupDefinition> groups = new ArrayList<>();
        for (PlayerGroupAssignment assignment : assignmentRepository.findAll(identity)) {
            groupRepository.find(assignment.groupId()).ifPresent(groups::add);
        }
        return List.copyOf(groups);
    }

    private static List<PlayerGroupDefinition> resolveGroups(
            PlayerAccessStateSnapshot state,
            PlayerIdentity identity
    ) {
        List<PlayerGroupDefinition> groups = new ArrayList<>();
        for (PlayerGroupAssignment assignment : state.assignments()) {
            if (matchesRemoteIdentity(assignment.identity(), identity)) {
                state.groups().stream()
                        .filter(group -> group.id().equals(assignment.groupId()))
                        .findFirst()
                        .ifPresent(groups::add);
            }
        }
        return List.copyOf(groups);
    }

    private static boolean matchesRemoteIdentity(PlayerIdentity source, PlayerIdentity target) {
        if (source.equals(target)) {
            return true;
        }
        return source.technicalUuid().isPresent()
                && target.technicalUuid().isPresent()
                && source.technicalUuid().get().equals(target.technicalUuid().get());
    }

    private static List<EffectiveAccess> resolveAccess(
            List<AccessGrant> accessGrants,
            List<PlayerGroupDefinition> groups
    ) {
        Map<AccessScope, List<AccessOrigin>> originsByScope = new LinkedHashMap<>();
        for (AccessGrant grant : accessGrants) {
            originsByScope.computeIfAbsent(grant.scope(), ignored -> new ArrayList<>())
                    .add(AccessOrigin.direct());
        }
        for (PlayerGroupDefinition group : groups) {
            for (var scope : group.accessGrants()) {
                originsByScope.computeIfAbsent(scope, ignored -> new ArrayList<>())
                        .add(AccessOrigin.group(group.id()));
            }
        }
        return originsByScope.entrySet().stream()
                .map(entry -> new EffectiveAccess(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<EffectivePermission> resolvePermissions(
            List<PlayerPermissionGrant> directPermissions,
            List<PlayerGroupDefinition> groups
    ) {
        Map<PermissionKey, List<AccessOrigin>> originsByPermission = new LinkedHashMap<>();
        for (PlayerPermissionGrant directGrant : directPermissions) {
            addPermissionOrigin(originsByPermission, directGrant.grant(), AccessOrigin.direct());
        }
        for (PlayerGroupDefinition group : groups) {
            for (PermissionGrant permission : group.permissions()) {
                addPermissionOrigin(originsByPermission, permission, AccessOrigin.group(group.id()));
            }
        }
        return originsByPermission.entrySet().stream()
                .map(entry -> new EffectivePermission(
                        new PermissionGrant(entry.getKey().scope(), entry.getKey().node()),
                        entry.getValue()
                ))
                .toList();
    }

    private static void addPermissionOrigin(
            Map<PermissionKey, List<AccessOrigin>> originsByPermission,
            PermissionGrant grant,
            AccessOrigin origin
    ) {
        originsByPermission.computeIfAbsent(
                        new PermissionKey(grant.scope(), grant.node()),
                        ignored -> new ArrayList<>()
                )
                .add(origin);
    }

    private record PermissionKey(
            AccessScope scope,
            String node
    ) {
    }
}
