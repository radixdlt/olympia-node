package org.radix.network2.messaging;

import org.junit.Test;
import org.radix.properties.RuntimeProperties;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageCentralConfigurationTest {

    @Test
    public void fromRuntimeProperties() {
        RuntimeProperties properties = mock(RuntimeProperties.class);

        when(properties.get(eq("messaging.inbound.queue_max"), anyInt())).thenReturn(100);
        when(properties.get(eq("messaging.inbound.threads"), anyInt())).thenReturn(101);
        when(properties.get(eq("messaging.outbound.queue_max"), anyInt())).thenReturn(102);
        when(properties.get(eq("messaging.outbound.threads"), anyInt())).thenReturn(103);
        when(properties.get(eq("messaging.time_to_live"), anyInt())).thenReturn(104);

        MessageCentralConfiguration config = MessageCentralConfiguration.fromRuntimeProperties(properties);

        assertEquals(100, config.messagingInboundQueueMax(-1));
        assertEquals(101, config.messagingInboundQueueThreads(-1));
        assertEquals(102, config.messagingOutboundQueueMax(-1));
        assertEquals(103, config.messagingOutboundQueueThreads(-1));
        assertEquals(104, config.messagingTimeToLive(-1));
    }
}