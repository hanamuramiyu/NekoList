package hanamuramiyu.monban.access;

import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.Objects;

public record PlayerAccessEvaluation(
        PlayerIdentity identity,
        AccessDecision decision
) {
    public PlayerAccessEvaluation {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(decision, "decision");
    }
}
