package hanamuramiyu.monban.storage.file.whitelist;

import hanamuramiyu.monban.identity.PlayerIdentity;
import hanamuramiyu.monban.whitelist.WhitelistAddResult;
import hanamuramiyu.monban.whitelist.WhitelistRemoveResult;
import hanamuramiyu.monban.whitelist.WhitelistUpdateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

class FileWhitelistRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileStartsWithEmptyWhitelist() throws IOException {
        Path file = whitelistFile();

        FileWhitelistRepository repository = new FileWhitelistRepository(file);

        assertTrue(repository.findAll().isEmpty());
        assertFalse(Files.exists(file));
    }

    @Test
    void savesAndLoadsOnlineIdentity() throws IOException {
        UUID uuid = testUuid(1);
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu_old", uuid);
        FileWhitelistRepository repository = new FileWhitelistRepository(whitelistFile());

        assertEquals(WhitelistAddResult.ADDED, repository.add(identity));

        FileWhitelistRepository reloaded = new FileWhitelistRepository(whitelistFile());
        PlayerIdentity stored = reloaded.find(PlayerIdentity.online("hanamuramiyu2", uuid)).orElseThrow();

        assertEquals("hanamuramiyu_old", stored.name());
        assertEquals(uuid, stored.verifiedUuid().orElseThrow());
        assertEquals(uuid, stored.technicalUuid().orElseThrow());
    }

    @Test
    void savesAndLoadsOfflineIdentityWithoutTechnicalUuid() throws IOException {
        PlayerIdentity identity = PlayerIdentity.offline("hanamuramiyu");
        FileWhitelistRepository repository = new FileWhitelistRepository(whitelistFile());

        repository.add(identity);

        PlayerIdentity stored = new FileWhitelistRepository(whitelistFile())
                .find(PlayerIdentity.offline("hanamuramiyu"))
                .orElseThrow();

        assertEquals("hanamuramiyu", stored.name());
        assertTrue(stored.technicalUuid().isEmpty());
        assertTrue(stored.verifiedUuid().isEmpty());
    }

    @Test
    void savesAndLoadsOfflineTechnicalUuidAndOriginalCase() throws IOException {
        UUID technicalUuid = testUuid(2);
        PlayerIdentity identity = PlayerIdentity.offline("HanamuraMiyu", technicalUuid);
        FileWhitelistRepository repository = new FileWhitelistRepository(whitelistFile());

        repository.add(identity);

        PlayerIdentity stored = new FileWhitelistRepository(whitelistFile())
                .find(PlayerIdentity.offline("HanamuraMiyu", testUuid(3)))
                .orElseThrow();

        assertEquals("HanamuraMiyu", stored.name());
        assertEquals(technicalUuid, stored.technicalUuid().orElseThrow());
        assertTrue(stored.verifiedUuid().isEmpty());
    }

    @Test
    void onlineDuplicateUsesVerifiedUuidInsteadOfName() throws IOException {
        UUID uuid = testUuid(4);
        FileWhitelistRepository repository = new FileWhitelistRepository(whitelistFile());

        assertEquals(WhitelistAddResult.ADDED, repository.add(PlayerIdentity.online("hanamuramiyu_old", uuid)));
        assertEquals(
                WhitelistAddResult.ALREADY_EXISTS,
                repository.add(PlayerIdentity.online("hanamuramiyu_new", uuid))
        );
        assertEquals(
                WhitelistAddResult.ADDED,
                repository.add(PlayerIdentity.online("hanamuramiyu_old", testUuid(5)))
        );
    }

    @Test
    void offlineDuplicateIgnoresCaseAndTechnicalUuid() throws IOException {
        FileWhitelistRepository repository = new FileWhitelistRepository(whitelistFile());

        assertEquals(
                WhitelistAddResult.ADDED,
                repository.add(PlayerIdentity.offline("hanamuramiyu", testUuid(6)))
        );
        assertEquals(
                WhitelistAddResult.ALREADY_EXISTS,
                repository.add(PlayerIdentity.offline("HanamuraMiyu", testUuid(7)))
        );
    }

    @Test
    void updateAndRemoveArePersisted() throws IOException {
        UUID onlineUuid = testUuid(8);
        PlayerIdentity onlineOld = PlayerIdentity.online("hanamuramiyu_old", onlineUuid);
        PlayerIdentity onlineNew = PlayerIdentity.online("hanamuramiyu_new", onlineUuid);
        PlayerIdentity offlineOld = PlayerIdentity.offline("hanamuramiyu_old");
        PlayerIdentity offlineNew = PlayerIdentity.offline("hanamuramiyu_new", testUuid(9));
        FileWhitelistRepository repository = new FileWhitelistRepository(whitelistFile());

        repository.add(onlineOld);
        repository.add(offlineOld);
        assertEquals(WhitelistUpdateResult.UPDATED, repository.update(onlineOld, onlineNew));
        assertEquals(WhitelistUpdateResult.UPDATED, repository.update(offlineOld, offlineNew));
        assertEquals(WhitelistRemoveResult.REMOVED, repository.remove(onlineNew));

        FileWhitelistRepository reloaded = new FileWhitelistRepository(whitelistFile());

        assertFalse(reloaded.contains(onlineNew));
        assertEquals("hanamuramiyu_new", reloaded.find(PlayerIdentity.offline("HANAMURAMIYU_NEW")).orElseThrow().name());
    }

    @Test
    void updateCannotOverwriteAnotherIdentity() throws IOException {
        PlayerIdentity first = PlayerIdentity.offline("hanamuramiyu");
        PlayerIdentity second = PlayerIdentity.offline("hanamuramiyu2");
        FileWhitelistRepository repository = new FileWhitelistRepository(whitelistFile());
        repository.add(first);
        repository.add(second);

        assertEquals(
                WhitelistUpdateResult.ALREADY_EXISTS,
                repository.update(first, PlayerIdentity.offline("HANAMURAMIYU2", testUuid(10)))
        );
        assertEquals(List.of(first, second), repository.findAll());
    }

    @Test
    void corruptedFileFailsToLoad() throws IOException {
        Files.writeString(
                whitelistFile(),
                "schema-version: 1\nentries:\n  - type: ONLINE\n    name: hanamuramiyu\n",
                StandardCharsets.UTF_8
        );

        assertThrows(IOException.class, () -> new FileWhitelistRepository(whitelistFile()));
    }

    @Test
    void findUsesLoadedInMemoryStateInsteadOfReadingDiskAgain() throws IOException {
        UUID uuid = testUuid(11);
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", uuid);
        FileWhitelistRepository repository = new FileWhitelistRepository(whitelistFile());
        repository.add(identity);

        Files.writeString(whitelistFile(), "this: [is: corrupted", StandardCharsets.UTF_8);

        assertEquals(identity, repository.find(PlayerIdentity.online("hanamuramiyu_new", uuid)).orElseThrow());
    }

    @Test
    void concurrentAddPersistsIdentityExactlyOnce() throws Exception {
        FileWhitelistRepository repository = new FileWhitelistRepository(whitelistFile());
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", testUuid(12));
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Callable<WhitelistAddResult>> tasks = java.util.stream.IntStream.range(0, 16)
                    .mapToObj(ignored -> (Callable<WhitelistAddResult>) () -> repository.add(identity))
                    .toList();
            List<Future<WhitelistAddResult>> futures = executor.invokeAll(tasks);

            long added = 0;
            for (Future<WhitelistAddResult> future : futures) {
                if (future.get() == WhitelistAddResult.ADDED) {
                    added++;
                }
            }

            assertEquals(1L, added);
            assertEquals(1, repository.findAll().size());
            assertEquals(1, new FileWhitelistRepository(whitelistFile()).findAll().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void successfulWriteLeavesNoTemporaryFile() throws IOException {
        FileWhitelistRepository repository = new FileWhitelistRepository(whitelistFile());

        repository.add(PlayerIdentity.offline("hanamuramiyu"));

        assertFalse(Files.exists(temporaryWhitelistFile()));
    }

    @Test
    void recoversValidTemporaryFileWhenPrimaryIsMissing() throws IOException {
        PlayerIdentity identity = PlayerIdentity.online("hanamuramiyu", testUuid(13));
        FileWhitelistRepository repository = new FileWhitelistRepository(whitelistFile());
        repository.add(identity);
        Files.move(whitelistFile(), temporaryWhitelistFile(), StandardCopyOption.REPLACE_EXISTING);

        FileWhitelistRepository recovered = new FileWhitelistRepository(whitelistFile());

        assertEquals(identity, recovered.find(identity).orElseThrow());
        assertTrue(Files.isRegularFile(whitelistFile()));
        assertFalse(Files.exists(temporaryWhitelistFile()));
    }

    @Test
    void invalidTemporaryFileDoesNotBecomeEmptyWhitelist() throws IOException {
        Files.writeString(
                temporaryWhitelistFile(),
                "schema-version: 1\nentries:\n  - type: ONLINE\n    name: hanamuramiyu\n",
                StandardCharsets.UTF_8
        );

        assertThrows(IOException.class, () -> new FileWhitelistRepository(whitelistFile()));
        assertFalse(Files.exists(whitelistFile()));
        assertTrue(Files.exists(temporaryWhitelistFile()));
    }

    @Test
    void validPrimaryWinsOverStaleTemporaryFile() throws IOException {
        PlayerIdentity primaryIdentity = PlayerIdentity.offline("hanamuramiyu");
        FileWhitelistRepository primaryRepository = new FileWhitelistRepository(whitelistFile());
        primaryRepository.add(primaryIdentity);

        Path staleFile = temporaryDirectory.resolve("stale.yml");
        PlayerIdentity staleIdentity = PlayerIdentity.offline("hanamuramiyu3");
        FileWhitelistRepository staleRepository = new FileWhitelistRepository(staleFile);
        staleRepository.add(staleIdentity);
        Files.move(staleFile, temporaryWhitelistFile(), StandardCopyOption.REPLACE_EXISTING);

        FileWhitelistRepository reloaded = new FileWhitelistRepository(whitelistFile());

        assertEquals(List.of(primaryIdentity), reloaded.findAll());
        assertFalse(reloaded.contains(staleIdentity));
        assertFalse(Files.exists(temporaryWhitelistFile()));
    }

    @Test
    void corruptedPrimaryDoesNotFallBackToValidTemporaryFile() throws IOException {
        Path recoverySource = temporaryDirectory.resolve("recovery-source.yml");
        PlayerIdentity recoveryIdentity = PlayerIdentity.offline("hanamuramiyu2");
        FileWhitelistRepository recoveryRepository = new FileWhitelistRepository(recoverySource);
        recoveryRepository.add(recoveryIdentity);
        Files.move(recoverySource, temporaryWhitelistFile(), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(whitelistFile(), "schema-version: [broken\n", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> new FileWhitelistRepository(whitelistFile()));
        assertTrue(Files.exists(temporaryWhitelistFile()));
    }

    @Test
    void fractionalSchemaVersionIsRejected() throws IOException {
        Files.writeString(
                whitelistFile(),
                "schema-version: 1.5\nentries: []\n",
                StandardCharsets.UTF_8
        );

        IOException exception = assertThrows(
                IOException.class,
                () -> new FileWhitelistRepository(whitelistFile())
        );

        assertTrue(exception.getMessage().contains("Expected integer at schema-version"));
    }

    @Test
    void unsupportedSchemaVersionIsRejected() throws IOException {
        Files.writeString(
                whitelistFile(),
                "schema-version: 2\nentries: []\n",
                StandardCharsets.UTF_8
        );

        IOException exception = assertThrows(
                IOException.class,
                () -> new FileWhitelistRepository(whitelistFile())
        );

        assertTrue(exception.getMessage().contains("Unsupported whitelist schema version: 2"));
    }

    @Test
    void unknownEntryFieldIsRejectedWithPath() throws IOException {
        Files.writeString(
                whitelistFile(),
                "schema-version: 1\n"
                        + "entries:\n"
                        + "  - type: OFFLINE\n"
                        + "    name: hanamuramiyu\n"
                        + "    technical-uudi: 12345678-1234-1234-1234-123456789abc\n",
                StandardCharsets.UTF_8
        );

        IOException exception = assertThrows(
                IOException.class,
                () -> new FileWhitelistRepository(whitelistFile())
        );

        assertTrue(exception.getMessage().contains("Unknown field at entries[0].technical-uudi"));
    }

    private static UUID testUuid(int index) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index));
    }

    private Path whitelistFile() {
        return temporaryDirectory.resolve("whitelist.yml");
    }

    private Path temporaryWhitelistFile() {
        return temporaryDirectory.resolve("whitelist.yml.tmp");
    }
}
