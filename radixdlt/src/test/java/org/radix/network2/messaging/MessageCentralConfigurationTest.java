/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.network2.messaging;

import org.junit.Test;

import com.radixdlt.properties.RuntimeProperties;

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