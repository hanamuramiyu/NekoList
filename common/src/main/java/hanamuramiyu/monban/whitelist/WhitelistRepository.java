package hanamuramiyu.monban.whitelist;

import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.List;
import java.util.Optional;

public interface WhitelistRepository {
    Optional<PlayerIdentity> find(PlayerIdentity identity);

    default boolean contains(PlayerIdentity identity) {
        return find(identity).isPresent();
    }

    List<PlayerIdentity> findAll();

    WhitelistAddResult add(PlayerIdentity identity);

    WhitelistRemoveResult remove(PlayerIdentity identity);

    WhitelistUpdateResult update(PlayerIdentity currentIdentity, PlayerIdentity updatedIdentity);
}
