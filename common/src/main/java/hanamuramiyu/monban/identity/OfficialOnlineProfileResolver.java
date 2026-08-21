package hanamuramiyu.monban.identity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OfficialOnlineProfileResolver implements OnlineProfileResolver, AutoCloseable {
    private static final URI API_ROOT = URI.create("https://api.mojang.com/users/profiles/minecraft/");
    private static final Pattern ID_FIELD = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([0-9a-fA-F]{32})\\\"");
    private static final Pattern NAME_FIELD = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([A-Za-z0-9_]{1,16})\\\"");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration SUCCESS_TTL = Duration.ofMinutes(5);
    private static final Duration NEGATIVE_TTL = Duration.ofSeconds(30);

    private final HttpClient client;
    private final Executor executor;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public OfficialOnlineProfileResolver() {
        this(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    public OfficialOnlineProfileResolver(HttpClient client, Executor executor) {
        this.client = Objects.requireNonNull(client, "client");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public CompletionStage<OnlineProfile> resolve(String name) {
        String normalized = PlayerIdentity.normalizeName(name);
        CacheEntry cached = cache.get(normalized);
        if (cached != null && cached.expiresAt() > System.nanoTime()) {
            return cached.result();
        }

        HttpRequest request = HttpRequest.newBuilder(API_ROOT.resolve(normalized))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        CompletableFuture<OnlineProfile> result = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenComposeAsync(this::parseResponse, executor)
                .toCompletableFuture();
        result.whenComplete((profile, failure) -> cache.put(
                normalized,
                new CacheEntry(result, System.nanoTime() + (failure == null ? SUCCESS_TTL : NEGATIVE_TTL).toNanos())
        ));
        return result;
    }

    private CompletableFuture<OnlineProfile> parseResponse(HttpResponse<String> response) {
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            return CompletableFuture.failedFuture(new OnlineProfileResolutionException(
                    OnlineProfileResolutionException.Kind.NOT_FOUND,
                    "Online profile was not found."
            ));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return CompletableFuture.failedFuture(new OnlineProfileResolutionException(
                    OnlineProfileResolutionException.Kind.UNAVAILABLE,
                    "Online profile lookup is temporarily unavailable."
            ));
        }

        Matcher idMatcher = ID_FIELD.matcher(response.body());
        Matcher nameMatcher = NAME_FIELD.matcher(response.body());
        if (!idMatcher.find() || !nameMatcher.find()) {
            return CompletableFuture.failedFuture(new OnlineProfileResolutionException(
                    OnlineProfileResolutionException.Kind.UNAVAILABLE,
                    "Official profile lookup returned an invalid response."
            ));
        }
        try {
            UUID uuid = UUID.fromString(idMatcher.group(1).replaceFirst(
                    "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                    "$1-$2-$3-$4-$5"
            ));
            return CompletableFuture.completedFuture(new OnlineProfile(nameMatcher.group(1), uuid));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(new OnlineProfileResolutionException(
                    OnlineProfileResolutionException.Kind.UNAVAILABLE,
                    "Official profile lookup returned an invalid UUID.",
                    exception
            ));
        }
    }

    @Override
    public void close() {
        if (executor instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private record CacheEntry(CompletableFuture<OnlineProfile> result, long expiresAt) {
    }
}
