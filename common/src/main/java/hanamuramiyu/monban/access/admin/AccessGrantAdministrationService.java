package hanamuramiyu.monban.access.admin;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.grant.AccessGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.List;
import java.util.Objects;

public final class AccessGrantAdministrationService {
    private final AccessGrantRepository repository;
    private final AccessGrantScopeValidator scopeValidator;

    public AccessGrantAdministrationService(
            AccessGrantRepository repository,
            AccessGrantScopeValidator scopeValidator
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scopeValidator = Objects.requireNonNull(scopeValidator, "scopeValidator");
    }

    public List<AccessGrant> findAll() {
        return repository.findAll();
    }

    public List<AccessGrant> findAll(AccessScope scope) {
        Objects.requireNonNull(scope, "scope");
        scopeValidator.validate(scope);
        return repository.findAll().stream()
                .filter(grant -> grant.scope().equals(scope))
                .toList();
    }

    public AccessGrantAddResult grant(AccessGrant grant) {
        Objects.requireNonNull(grant, "grant");
        scopeValidator.validate(grant.scope());
        return repository.add(grant);
    }

    public AccessGrantRemoveResult revoke(AccessScope scope, PlayerIdentity identity) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(identity, "identity");
        scopeValidator.validate(scope);
        return repository.remove(scope, identity);
    }
}
