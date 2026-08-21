package hanamuramiyu.monban.identity;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface OnlineProfileResolver {
    CompletionStage<OnlineProfile> resolve(String name);

    static OnlineProfileResolver unavailable() {
        return name -> {
            Objects.requireNonNull(name, "name");
            return CompletableFuture.failedFuture(new OnlineProfileResolutionException(
                    OnlineProfileResolutionException.Kind.UNAVAILABLE,
                    "Online profile lookup is unavailable."
            ));
        };
    }
}
