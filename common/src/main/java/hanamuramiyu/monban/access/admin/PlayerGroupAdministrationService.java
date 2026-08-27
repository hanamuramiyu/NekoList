package hanamuramiyu.monban.access.admin;

import hanamuramiyu.monban.access.group.PlayerGroupAddResult;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.PlayerGroupRemoveResult;
import hanamuramiyu.monban.access.group.PlayerGroupRepository;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PermissionGrantAddResult;
import hanamuramiyu.monban.access.permission.PermissionGrantRemoveResult;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PlayerGroupAdministrationService {
    private final PlayerGroupRepository groupRepository;
    private final PlayerGroupAssignmentRepository assignmentRepository;
    private final PlayerPermissionGrantRepository permissionRepository;
    private final AccessGrantScopeValidator scopeValidator;

    public PlayerGroupAdministrationService(
            PlayerGroupRepository groupRepository,
            PlayerGroupAssignmentRepository assignmentRepository,
            PlayerPermissionGrantRepository permissionRepository,
            AccessGrantScopeValidator scopeValidator
    ) {
        this.groupRepository = Objects.requireNonNull(groupRepository, "groupRepository");
        this.assignmentRepository = Objects.requireNonNull(assignmentRepository, "assignmentRepository");
        this.permissionRepository = Objects.requireNonNull(permissionRepository, "permissionRepository");
        this.scopeValidator = Objects.requireNonNull(scopeValidator, "scopeValidator");
    }

    public List<PlayerGroupDefinition> findGroups() {
        return groupRepository.findAll();
    }

    public Optional<PlayerGroupDefinition> findGroup(String groupId) {
        return groupRepository.find(groupId);
    }

    public PlayerGroupAddResult createGroup(String groupId) {
        return groupRepository.add(new PlayerGroupDefinition(groupId, List.of(), List.of()));
    }

    public PlayerGroupRemoveResult removeGroup(String groupId) {
        PlayerGroupRemoveResult result = groupRepository.remove(groupId);
        if (result == PlayerGroupRemoveResult.REMOVED) {
            assignmentRepository.findAll().stream()
                    .filter(assignment -> assignment.groupId().equals(groupId))
                    .forEach(assignmentRepository::remove);
        }
        return result;
    }

    public PlayerGroupEntryResult grantAccess(String groupId, AccessScope scope) {
        scopeValidator.validate(scope);
        PlayerGroupDefinition group = requireGroup(groupId);
        if (group.accessGrants().contains(scope)) {
            return PlayerGroupEntryResult.ALREADY_EXISTS;
        }
        PlayerGroupDefinition updated = new PlayerGroupDefinition(
                group.id(),
                append(group.accessGrants(), scope),
                group.permissions()
        );
        groupRepository.update(updated);
        return PlayerGroupEntryResult.ADDED;
    }

    public PlayerGroupEntryResult revokeAccess(String groupId, AccessScope scope) {
        scopeValidator.validate(scope);
        PlayerGroupDefinition group = requireGroup(groupId);
        if (!group.accessGrants().contains(scope)) {
            return PlayerGroupEntryResult.NOT_FOUND;
        }
        PlayerGroupDefinition updated = new PlayerGroupDefinition(
                group.id(),
                remove(group.accessGrants(), scope),
                group.permissions()
        );
        groupRepository.update(updated);
        return PlayerGroupEntryResult.REMOVED;
    }

    public PlayerGroupEntryResult grantPermission(String groupId, PermissionGrant grant) {
        scopeValidator.validate(grant.scope());
        PlayerGroupDefinition group = requireGroup(groupId);
        if (group.permissions().contains(grant)) {
            return PlayerGroupEntryResult.ALREADY_EXISTS;
        }
        PlayerGroupDefinition updated = new PlayerGroupDefinition(
                group.id(),
                group.accessGrants(),
                append(group.permissions(), grant)
        );
        groupRepository.update(updated);
        return PlayerGroupEntryResult.ADDED;
    }

    public PlayerGroupEntryResult revokePermission(String groupId, PermissionGrant grant) {
        scopeValidator.validate(grant.scope());
        PlayerGroupDefinition group = requireGroup(groupId);
        if (!group.permissions().contains(grant)) {
            return PlayerGroupEntryResult.NOT_FOUND;
        }
        PlayerGroupDefinition updated = new PlayerGroupDefinition(
                group.id(),
                group.accessGrants(),
                remove(group.permissions(), grant)
        );
        groupRepository.update(updated);
        return PlayerGroupEntryResult.REMOVED;
    }

    public PlayerGroupEntryResult addAssignment(PlayerIdentity identity, String groupId) {
        requireGroup(groupId);
        return switch (assignmentRepository.add(new PlayerGroupAssignment(identity, groupId))) {
            case ADDED -> PlayerGroupEntryResult.ADDED;
            case ALREADY_EXISTS -> PlayerGroupEntryResult.ALREADY_EXISTS;
        };
    }

    public PlayerGroupEntryResult removeAssignment(PlayerIdentity identity, String groupId) {
        return switch (assignmentRepository.remove(new PlayerGroupAssignment(identity, groupId))) {
            case REMOVED -> PlayerGroupEntryResult.REMOVED;
            case NOT_FOUND -> PlayerGroupEntryResult.NOT_FOUND;
        };
    }

    public PermissionGrantAddResult addDirectPermission(PlayerIdentity identity, PermissionGrant grant) {
        scopeValidator.validate(grant.scope());
        return permissionRepository.add(new PlayerPermissionGrant(identity, grant));
    }

    public PermissionGrantRemoveResult removeDirectPermission(PlayerIdentity identity, PermissionGrant grant) {
        scopeValidator.validate(grant.scope());
        return permissionRepository.remove(new PlayerPermissionGrant(identity, grant));
    }

    private PlayerGroupDefinition requireGroup(String groupId) {
        return groupRepository.find(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown player group: " + groupId));
    }

    private static <T> List<T> append(List<T> values, T value) {
        List<T> result = new ArrayList<>(values);
        result.add(value);
        return List.copyOf(result);
    }

    private static <T> List<T> remove(List<T> values, T value) {
        return values.stream().filter(existing -> !existing.equals(value)).toList();
    }
}
