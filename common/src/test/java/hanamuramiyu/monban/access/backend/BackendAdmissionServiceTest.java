package hanamuramiyu.monban.access.backend;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantLookup;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import hanamuramiyu.monban.access.effective.PlayerAccessResolver;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendAdmissionServiceTest {
    private static final PlayerIdentity IDENTITY = PlayerIdentity.online("hanamuramiyu", UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test void defaultOpenAllowsWithoutLookup() {
        RecordingAccessGrantLookup lookup = new RecordingAccessGrantLookup(AccessScope.server("lobby"));
        BackendAdmissionDecision result = service(BackendAccessMode.OPEN, Map.of(), Map.of(), lookup)
                .evaluate(BackendTarget.ungrouped("lobby"), IDENTITY);
        assertEquals(BackendAdmissionDecision.ALLOWED, result);
        assertEquals(0, lookup.scopes.size());
    }

    @Test void defaultGrantRequiredDeniesWithoutGrant() {
        assertEquals(BackendAdmissionDecision.NOT_GRANTED,
                service(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of(), new RecordingAccessGrantLookup())
                        .evaluate(BackendTarget.ungrouped("lobby"), IDENTITY));
    }

    @Test void groupPolicyOverridesDefault() {
        assertEquals(BackendAdmissionDecision.NOT_GRANTED,
                service(BackendAccessMode.OPEN, Map.of("testing", BackendAccessMode.GRANT_REQUIRED), Map.of(), new RecordingAccessGrantLookup())
                        .evaluate(BackendTarget.grouped("test-lobby", "testing"), IDENTITY));
    }

    @Test void openGroupPolicyOverridesRestrictedDefaultWithoutLookup() {
        RecordingAccessGrantLookup lookup = new RecordingAccessGrantLookup(AccessScope.server("lobby"));
        assertEquals(BackendAdmissionDecision.ALLOWED,
                service(BackendAccessMode.GRANT_REQUIRED, Map.of("public", BackendAccessMode.OPEN), Map.of(), lookup)
                        .evaluate(BackendTarget.grouped("lobby", "public"), IDENTITY));
        assertEquals(0, lookup.scopes.size());
    }

    @Test void serverOpenOverridesRestrictedGroupWithoutLookup() {
        RecordingAccessGrantLookup lookup = new RecordingAccessGrantLookup();
        assertEquals(BackendAdmissionDecision.ALLOWED,
                service(BackendAccessMode.GRANT_REQUIRED, Map.of("testing", BackendAccessMode.GRANT_REQUIRED), Map.of("test-lobby", BackendAccessMode.OPEN), lookup)
                        .evaluate(BackendTarget.grouped("test-lobby", "testing"), IDENTITY));
        assertEquals(0, lookup.scopes.size());
    }

    @Test void serverGrantRequiredOverridesOpenGroup() {
        assertEquals(BackendAdmissionDecision.NOT_GRANTED,
                service(BackendAccessMode.OPEN, Map.of("public", BackendAccessMode.OPEN), Map.of("lobby", BackendAccessMode.GRANT_REQUIRED), new RecordingAccessGrantLookup())
                        .evaluate(BackendTarget.grouped("lobby", "public"), IDENTITY));
    }

    @Test void restrictedGroupedServerAllowsDirectServerGrant() {
        RecordingAccessGrantLookup lookup = new RecordingAccessGrantLookup(AccessScope.server("test-lobby"));
        assertEquals(BackendAdmissionDecision.ALLOWED,
                service(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of(), lookup)
                        .evaluate(BackendTarget.grouped("test-lobby", "testing"), IDENTITY));
        assertEquals(List.of(AccessScope.server("test-lobby")), lookup.scopes);
    }

    @Test void restrictedGroupedServerAllowsGroupGrant() {
        RecordingAccessGrantLookup lookup = new RecordingAccessGrantLookup(AccessScope.serverGroup("testing"));
        assertEquals(BackendAdmissionDecision.ALLOWED,
                service(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of(), lookup)
                        .evaluate(BackendTarget.grouped("test-lobby", "testing"), IDENTITY));
        assertEquals(List.of(AccessScope.server("test-lobby"), AccessScope.serverGroup("testing")), lookup.scopes);
    }

    @Test void restrictedGroupedServerWithoutGrantIsDenied() {
        RecordingAccessGrantLookup lookup = new RecordingAccessGrantLookup();
        assertEquals(BackendAdmissionDecision.NOT_GRANTED,
                service(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of(), lookup)
                        .evaluate(BackendTarget.grouped("test-lobby", "testing"), IDENTITY));
        assertEquals(List.of(AccessScope.server("test-lobby"), AccessScope.serverGroup("testing")), lookup.scopes);
    }

    @Test void restrictedUngroupedServerAllowsOnlyServerGrant() {
        RecordingAccessGrantLookup lookup = new RecordingAccessGrantLookup(AccessScope.server("dev"));
        assertEquals(BackendAdmissionDecision.ALLOWED,
                service(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of(), lookup)
                        .evaluate(BackendTarget.ungrouped("dev"), IDENTITY));
        assertEquals(List.of(AccessScope.server("dev")), lookup.scopes);
    }

    @Test void restrictedServerAllowsGroupServerGrant() {
        InMemoryAccessGrantRepository directGrants = new InMemoryAccessGrantRepository();
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        groups.add(new PlayerGroupDefinition("moderator", List.of(AccessScope.server("survival")), List.of()));
        assignments.add(new PlayerGroupAssignment(IDENTITY, "moderator"));
        PlayerAccessResolver resolver = new PlayerAccessResolver(
                directGrants,
                groups,
                assignments,
                new InMemoryPlayerPermissionGrantRepository()
        );
        BackendAdmissionService service = new BackendAdmissionService(
                new BackendAccessPolicyCatalog(BackendAccessMode.GRANT_REQUIRED, Map.of(), Map.of()),
                directGrants,
                resolver
        );

        assertEquals(
                BackendAdmissionDecision.ALLOWED,
                service.evaluate(BackendTarget.ungrouped("survival"), IDENTITY)
        );
    }

    private static BackendAdmissionService service(BackendAccessMode d, Map<String, BackendAccessMode> g, Map<String, BackendAccessMode> s, AccessGrantLookup l) {
        return new BackendAdmissionService(new BackendAccessPolicyCatalog(d, g, s), l);
    }

    private static final class RecordingAccessGrantLookup implements AccessGrantLookup {
        private final List<AccessScope> granted;
        private final List<AccessScope> scopes = new ArrayList<>();
        private RecordingAccessGrantLookup(AccessScope... granted) { this.granted = List.of(granted); }
        @Override public Optional<AccessGrant> find(AccessScope scope, PlayerIdentity identity) {
            scopes.add(scope);
            return granted.contains(scope) ? Optional.of(new AccessGrant(scope, identity)) : Optional.empty();
        }
    }
}
