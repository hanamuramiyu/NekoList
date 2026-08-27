package hanamuramiyu.monban.access.group;

import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.Objects;

public record PlayerGroupAssignment(PlayerIdentity identity, String groupId) {
    public PlayerGroupAssignment {
        Objects.requireNonNull(identity, "identity");
        groupId = PlayerGroupDefinition.requireId(groupId);
    }
}
