package com.radixdlt.network.messaging;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class MessageFromPeerTest {

    @Test
    public void test_equals() {
        EqualsVerifier.forClass(MessageFromPeer.class)
            .verify();
    }
}
