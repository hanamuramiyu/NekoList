package hanamuramiyu.monban.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitelistPolicyTest {
    @Test
    void stateCanBeReplacedWithoutChangingObjectIdentity() {
        WhitelistPolicy policy = new WhitelistPolicy(true);

        assertTrue(policy.enabled());
        assertTrue(policy.setEnabled(false));
        assertFalse(policy.enabled());
        assertFalse(policy.setEnabled(false));
        assertFalse(policy.setEnabled(true));
        assertTrue(policy.enabled());
    }
}
