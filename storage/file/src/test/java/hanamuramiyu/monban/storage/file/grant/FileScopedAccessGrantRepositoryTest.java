package hanamuramiyu.monban.storage.file.grant;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.grant.AccessGrantAddResult;
import hanamuramiyu.monban.access.grant.AccessGrantRemoveResult;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileScopedAccessGrantRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileStartsEmptyWithoutCreatingFile() throws IOException {
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());

        assertTrue(repository.findAll().isEmpty());
        assertFalse(Files.exists(grantsFile()));
    }

    @Test
    void roundTripsOnlineServerGroupGrant() throws IOException {
        UUID uuid = testUuid(1);
        AccessGrant grant = new AccessGrant(
                AccessScope.serverGroup("testing"),
                PlayerIdentity.online("hanamuramiyu_old", uuid)
        );
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());

        assertEquals(AccessGrantAddResult.ADDED, repository.add(grant));

        FileScopedAccessGrantRepository reloaded = new FileScopedAccessGrantRepository(grantsFile());
        AccessGrant stored = reloaded.find(
                AccessScope.serverGroup("testing"),
                PlayerIdentity.online("hanamuramiyu_new", uuid)
        ).orElseThrow();

        assertEquals(AccessScope.serverGroup("testing"), stored.scope());
        assertEquals("hanamuramiyu_old", stored.identity().name());
        assertEquals(uuid, stored.identity().verifiedUuid().orElseThrow());
    }

    @Test
    void roundTripsOfflineWithoutTechnicalUuid() throws IOException {
        AccessGrant grant = new AccessGrant(
                AccessScope.server("dev"),
                PlayerIdentity.offline("hanamuramiyu")
        );
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());
        repository.add(grant);

        AccessGrant stored = new FileScopedAccessGrantRepository(grantsFile())
                .find(AccessScope.server("dev"), PlayerIdentity.offline("hanamuramiyu"))
                .orElseThrow();

        assertEquals("hanamuramiyu", stored.identity().name());
        assertTrue(stored.identity().technicalUuid().isEmpty());
    }

    @Test
    void roundTripsOfflineWithTechnicalUuid() throws IOException {
        UUID technicalUuid = testUuid(2);
        AccessGrant grant = new AccessGrant(
                AccessScope.serverGroup("testing"),
                PlayerIdentity.offline("hanamuramiyu", technicalUuid)
        );
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());
        repository.add(grant);

        AccessGrant stored = new FileScopedAccessGrantRepository(grantsFile())
                .find(
                        AccessScope.serverGroup("testing"),
                        PlayerIdentity.offline("hanamuramiyu", testUuid(3))
                )
                .orElseThrow();

        assertEquals(technicalUuid, stored.identity().technicalUuid().orElseThrow());
        assertTrue(stored.identity().verifiedUuid().isEmpty());
    }

    @Test
    void roundTripsServerGrant() throws IOException {
        AccessGrant grant = new AccessGrant(
                AccessScope.server("staging"),
                PlayerIdentity.offline("hanamuramiyu")
        );
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());

        repository.add(grant);

        assertEquals(
                grant,
                new FileScopedAccessGrantRepository(grantsFile())
                        .find(AccessScope.server("staging"), PlayerIdentity.offline("hanamuramiyu"))
                        .orElseThrow()
        );
    }

    @Test
    void sameIdentityCanExistInMultipleScopes() throws IOException {
        UUID uuid = testUuid(4);
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", uuid);
        AccessGrant groupGrant = new AccessGrant(AccessScope.serverGroup("testing"), identity);
        AccessGrant serverGrant = new AccessGrant(AccessScope.server("dev"), identity);
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());

        assertEquals(AccessGrantAddResult.ADDED, repository.add(groupGrant));
        assertEquals(AccessGrantAddResult.ADDED, repository.add(serverGrant));
        assertEquals(List.of(groupGrant, serverGrant), repository.findAll());
    }

    @Test
    void duplicateOnlineUuidInSameScopeIsRejected() throws IOException {
        UUID uuid = testUuid(5);
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());

        assertEquals(
                AccessGrantAddResult.ADDED,
                repository.add(new AccessGrant(
                        AccessScope.serverGroup("testing"),
                        PlayerIdentity.online("hanamuramiyu_old", uuid)
                ))
        );
        assertEquals(
                AccessGrantAddResult.ALREADY_EXISTS,
                repository.add(new AccessGrant(
                        AccessScope.serverGroup("testing"),
                        PlayerIdentity.online("hanamuramiyu_new", uuid)
                ))
        );
    }

    @Test
    void duplicateOfflineNameIgnoringCaseIsRejected() throws IOException {
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());

        assertEquals(
                AccessGrantAddResult.ADDED,
                repository.add(new AccessGrant(
                        AccessScope.server("dev"),
                        PlayerIdentity.offline("hanamuramiyu", testUuid(6))
                ))
        );
        assertEquals(
                AccessGrantAddResult.ALREADY_EXISTS,
                repository.add(new AccessGrant(
                        AccessScope.server("dev"),
                        PlayerIdentity.offline("HanamuraMiyu", testUuid(7))
                ))
        );
    }

    @Test
    void networkScopeIsRejectedByAllScopedOperations() throws IOException {
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());
        PlayerIdentity identity = PlayerIdentity.offline("hanamuramiyu");

        assertThrows(IllegalArgumentException.class, () -> repository.find(AccessScope.network(), identity));
        assertThrows(IllegalArgumentException.class, () -> repository.contains(AccessScope.network(), identity));
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.add(new AccessGrant(AccessScope.network(), identity))
        );
        assertThrows(IllegalArgumentException.class, () -> repository.remove(AccessScope.network(), identity));
    }

    @Test
    void networkScopeInFileIsRejected() throws IOException {
        write("""
                schema-version: 1
                grants:
                  - scope:
                      type: NETWORK
                    identity:
                      type: OFFLINE
                      name: hanamuramiyu
                """);

        assertThrows(IOException.class, () -> new FileScopedAccessGrantRepository(grantsFile()));
    }

    @Test
    void addAndRemoveArePersisted() throws IOException {
        AccessGrant first = new AccessGrant(
                AccessScope.serverGroup("testing"),
                PlayerIdentity.offline("hanamuramiyu")
        );
        AccessGrant second = new AccessGrant(
                AccessScope.server("dev"),
                PlayerIdentity.offline("hanamuramiyu2")
        );
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());

        assertEquals(AccessGrantAddResult.ADDED, repository.add(first));
        assertEquals(AccessGrantAddResult.ADDED, repository.add(second));
        assertEquals(
                AccessGrantRemoveResult.REMOVED,
                repository.remove(first.scope(), first.identity())
        );

        FileScopedAccessGrantRepository reloaded = new FileScopedAccessGrantRepository(grantsFile());
        assertFalse(reloaded.contains(first.scope(), first.identity()));
        assertTrue(reloaded.contains(second.scope(), second.identity()));
    }

    @Test
    void findUsesLoadedInMemorySnapshot() throws IOException {
        AccessGrant grant = new AccessGrant(
                AccessScope.server("dev"),
                PlayerIdentity.offline("hanamuramiyu")
        );
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());
        repository.add(grant);

        Files.writeString(grantsFile(), "this: [is: corrupted", StandardCharsets.UTF_8);

        assertEquals(grant, repository.find(grant.scope(), grant.identity()).orElseThrow());
    }

    @Test
    void malformedYamlIsRejected() throws IOException {
        write("schema-version: 1\ngrants: [broken\n");

        assertThrows(IOException.class, () -> new FileScopedAccessGrantRepository(grantsFile()));
    }

    @Test
    void unknownFieldsAreRejected() throws IOException {
        write("""
                schema-version: 1
                grants:
                  - scope:
                      type: SERVER
                      id: dev
                      unknown: nope
                    identity:
                      type: OFFLINE
                      name: hanamuramiyu
                """);

        IOException exception = assertThrows(
                IOException.class,
                () -> new FileScopedAccessGrantRepository(grantsFile())
        );

        assertTrue(exception.getMessage().contains("Unknown field at grants[0].scope.unknown"));
    }

    @Test
    void duplicateYamlKeysAreRejected() throws IOException {
        write("""
                schema-version: 1
                grants:
                  - scope:
                      type: SERVER
                      type: SERVER_GROUP
                      id: dev
                    identity:
                      type: OFFLINE
                      name: hanamuramiyu
                """);

        assertThrows(IOException.class, () -> new FileScopedAccessGrantRepository(grantsFile()));
    }

    @Test
    void yamlAliasesAreRejected() throws IOException {
        write("""
                schema-version: 1
                grants:
                  - &grant
                    scope:
                      type: SERVER
                      id: dev
                    identity:
                      type: OFFLINE
                      name: hanamuramiyu
                  - *grant
                """);

        IOException exception = assertThrows(
                IOException.class,
                () -> new FileScopedAccessGrantRepository(grantsFile())
        );

        assertTrue(exception.getMessage().contains("aliases"));
    }

    @Test
    void multipleDocumentsAreRejected() throws IOException {
        write("""
                schema-version: 1
                grants: []
                ---
                schema-version: 1
                grants: []
                """);

        IOException exception = assertThrows(
                IOException.class,
                () -> new FileScopedAccessGrantRepository(grantsFile())
        );

        assertTrue(exception.getMessage().contains("exactly one YAML document"));
    }

    @Test
    void fractionalSchemaVersionIsRejected() throws IOException {
        write("schema-version: 1.5\ngrants: []\n");

        IOException exception = assertThrows(
                IOException.class,
                () -> new FileScopedAccessGrantRepository(grantsFile())
        );

        assertTrue(exception.getMessage().contains("Expected integer at schema-version"));
    }

    @Test
    void unsupportedSchemaVersionIsRejected() throws IOException {
        write("schema-version: 2\ngrants: []\n");

        IOException exception = assertThrows(
                IOException.class,
                () -> new FileScopedAccessGrantRepository(grantsFile())
        );

        assertTrue(exception.getMessage().contains("Unsupported access grants schema version: 2"));
    }

    @Test
    void serverGroupWithoutIdIsRejected() throws IOException {
        write("""
                schema-version: 1
                grants:
                  - scope:
                      type: SERVER_GROUP
                    identity:
                      type: OFFLINE
                      name: hanamuramiyu
                """);

        assertThrows(IOException.class, () -> new FileScopedAccessGrantRepository(grantsFile()));
    }

    @Test
    void serverWithoutIdIsRejected() throws IOException {
        write("""
                schema-version: 1
                grants:
                  - scope:
                      type: SERVER
                    identity:
                      type: OFFLINE
                      name: hanamuramiyu
                """);

        assertThrows(IOException.class, () -> new FileScopedAccessGrantRepository(grantsFile()));
    }

    @Test
    void invalidIdentityIsRejected() throws IOException {
        write("""
                schema-version: 1
                grants:
                  - scope:
                      type: SERVER
                      id: dev
                    identity:
                      type: ONLINE
                      name: hanamuramiyu
                """);

        assertThrows(IOException.class, () -> new FileScopedAccessGrantRepository(grantsFile()));
    }

    @Test
    void duplicateOnlineUuidInFileIsRejected() throws IOException {
        UUID uuid = testUuid(8);
        write("""
                schema-version: 1
                grants:
                  - scope:
                      type: SERVER_GROUP
                      id: testing
                    identity:
                      type: ONLINE
                      name: hanamuramiyu_old
                      verified-uuid: %s
                  - scope:
                      type: SERVER_GROUP
                      id: testing
                    identity:
                      type: ONLINE
                      name: hanamuramiyu_new
                      verified-uuid: %s
                """.formatted(uuid, uuid));

        assertThrows(IOException.class, () -> new FileScopedAccessGrantRepository(grantsFile()));
    }

    @Test
    void duplicateOfflineNameInFileIgnoringCaseIsRejected() throws IOException {
        write("""
                schema-version: 1
                grants:
                  - scope:
                      type: SERVER
                      id: dev
                    identity:
                      type: OFFLINE
                      name: hanamuramiyu
                  - scope:
                      type: SERVER
                      id: dev
                    identity:
                      type: OFFLINE
                      name: HanamuraMiyu
                """);

        assertThrows(IOException.class, () -> new FileScopedAccessGrantRepository(grantsFile()));
    }

    @Test
    void persistenceFailureLeavesMemoryUnchanged() throws IOException {
        Path blockedParent = temporaryDirectory.resolve("blocked");
        Path file = blockedParent.resolve("access-grants.yml");
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(file);
        Files.writeString(blockedParent, "not a directory", StandardCharsets.UTF_8);

        AccessGrant grant = new AccessGrant(
                AccessScope.server("dev"),
                PlayerIdentity.offline("hanamuramiyu")
        );

        assertThrows(UncheckedIOException.class, () -> repository.add(grant));
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void concurrentSameGrantAddPersistsExactlyOnce() throws Exception {
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());
        AccessGrant grant = new AccessGrant(
                AccessScope.serverGroup("testing"),
                PlayerIdentity.online("hanamuramiyu", testUuid(9))
        );

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Callable<AccessGrantAddResult>> tasks = java.util.stream.IntStream.range(0, 16)
                    .mapToObj(ignored -> (Callable<AccessGrantAddResult>) () -> repository.add(grant))
                    .toList();
            List<Future<AccessGrantAddResult>> futures = executor.invokeAll(tasks);

            long added = 0;
            for (Future<AccessGrantAddResult> future : futures) {
                if (future.get() == AccessGrantAddResult.ADDED) {
                    added++;
                }
            }

            assertEquals(1L, added);
            assertEquals(1, repository.findAll().size());
            assertEquals(1, new FileScopedAccessGrantRepository(grantsFile()).findAll().size());
        }
    }

    @Test
    void successfulWriteRemovesTemporaryFile() throws IOException {
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());

        repository.add(new AccessGrant(AccessScope.server("dev"), PlayerIdentity.offline("hanamuramiyu")));

        assertFalse(Files.exists(temporaryGrantsFile()));
    }

    @Test
    void recoversValidTemporaryFileWhenPrimaryIsMissing() throws IOException {
        AccessGrant grant = new AccessGrant(
                AccessScope.server("dev"),
                PlayerIdentity.offline("hanamuramiyu")
        );
        FileScopedAccessGrantRepository repository = new FileScopedAccessGrantRepository(grantsFile());
        repository.add(grant);
        Files.move(grantsFile(), temporaryGrantsFile(), StandardCopyOption.REPLACE_EXISTING);

        FileScopedAccessGrantRepository recovered = new FileScopedAccessGrantRepository(grantsFile());

        assertEquals(grant, recovered.find(grant.scope(), grant.identity()).orElseThrow());
        assertTrue(Files.isRegularFile(grantsFile()));
        assertFalse(Files.exists(temporaryGrantsFile()));
    }

    @Test
    void invalidTemporaryRecoveryIsRejectedAndKept() throws IOException {
        Files.writeString(
                temporaryGrantsFile(),
                "schema-version: 1\ngrants:\n  - scope:\n      type: SERVER\n",
                StandardCharsets.UTF_8
        );

        assertThrows(IOException.class, () -> new FileScopedAccessGrantRepository(grantsFile()));
        assertFalse(Files.exists(grantsFile()));
        assertTrue(Files.exists(temporaryGrantsFile()));
    }

    @Test
    void validPrimaryWinsOverStaleTemporaryFile() throws IOException {
        AccessGrant primary = new AccessGrant(
                AccessScope.server("dev"),
                PlayerIdentity.offline("hanamuramiyu")
        );
        FileScopedAccessGrantRepository primaryRepository = new FileScopedAccessGrantRepository(grantsFile());
        primaryRepository.add(primary);

        Path staleFile = temporaryDirectory.resolve("stale.yml");
        AccessGrant stale = new AccessGrant(
                AccessScope.server("staging"),
                PlayerIdentity.offline("hanamuramiyu3")
        );
        FileScopedAccessGrantRepository staleRepository = new FileScopedAccessGrantRepository(staleFile);
        staleRepository.add(stale);
        Files.move(staleFile, temporaryGrantsFile(), StandardCopyOption.REPLACE_EXISTING);

        FileScopedAccessGrantRepository reloaded = new FileScopedAccessGrantRepository(grantsFile());

        assertEquals(List.of(primary), reloaded.findAll());
        assertFalse(reloaded.contains(stale.scope(), stale.identity()));
        assertFalse(Files.exists(temporaryGrantsFile()));
    }

    @Test
    void corruptPrimaryDoesNotFallBackToValidTemporaryFile() throws IOException {
        Path recoverySource = temporaryDirectory.resolve("recovery-source.yml");
        AccessGrant recovery = new AccessGrant(
                AccessScope.server("recovery"),
                PlayerIdentity.offline("hanamuramiyu2")
        );
        FileScopedAccessGrantRepository recoveryRepository = new FileScopedAccessGrantRepository(recoverySource);
        recoveryRepository.add(recovery);
        Files.move(recoverySource, temporaryGrantsFile(), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(grantsFile(), "schema-version: [broken\n", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> new FileScopedAccessGrantRepository(grantsFile()));
        assertTrue(Files.exists(temporaryGrantsFile()));
    }

    private void write(String contents) throws IOException {
        Files.writeString(grantsFile(), contents, StandardCharsets.UTF_8);
    }

    private static UUID testUuid(int index) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index));
    }

    private Path grantsFile() {
        return temporaryDirectory.resolve("access-grants.yml");
    }

    private Path temporaryGrantsFile() {
        return temporaryDirectory.resolve("access-grants.yml.tmp");
    }
}
