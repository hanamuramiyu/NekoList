package hanamuramiyu.monban.access.backend;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendAccessPolicyCatalogTest {
    @Test void serverOverridesGroupAndGroupOverridesDefault() {
        BackendAccessPolicyCatalog catalog = new BackendAccessPolicyCatalog(
                BackendAccessMode.OPEN,
                Map.of("testing", BackendAccessMode.GRANT_REQUIRED),
                Map.of("test-lobby", BackendAccessMode.OPEN, "private", BackendAccessMode.GRANT_REQUIRED)
        );
        assertEquals(BackendAccessMode.OPEN, catalog.effectiveMode(BackendTarget.grouped("test-lobby", "testing")));
        assertEquals(BackendAccessMode.GRANT_REQUIRED, catalog.effectiveMode(BackendTarget.grouped("test-survival", "testing")));
        assertEquals(BackendAccessMode.OPEN, catalog.effectiveMode(BackendTarget.ungrouped("other")));
        assertEquals(BackendAccessMode.GRANT_REQUIRED, catalog.effectiveMode(BackendTarget.ungrouped("private")));
    }
}
