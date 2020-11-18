/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.epoch;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.PacemakerInfoSender;
import com.radixdlt.consensus.liveness.PacemakerTimeoutCalculator;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;
import java.util.function.Consumer;

import static org.mockito.Mockito.*;

public class LocalViewUpdateSenderWithTimeoutTest {

    private static final Random random = new Random();

    private PacemakerTimeoutSender timeoutSender = mock(PacemakerTimeoutSender.class);
    private PacemakerTimeoutCalculator timeoutCalculator = mock(PacemakerTimeoutCalculator.class);
    private PacemakerInfoSender pacemakerInfoSender = mock(PacemakerInfoSender.class);
    @SuppressWarnings("unchecked")
    private Consumer<LocalViewUpdate> viewUpdateConsumer = mock(Consumer.class);

    private LocalViewUpdateSenderWithTimeout localViewUpdateSenderWithTimeout;

    @Before
    public void setup() {
        localViewUpdateSenderWithTimeout = new LocalViewUpdateSenderWithTimeout(
            timeoutSender,
            timeoutCalculator,
            pacemakerInfoSender,
            viewUpdateConsumer
        );
    }

    @Test
    public void when_view_update_is_sent__then_timeout_is_scheduled() {
        final LocalViewUpdate viewUpdate = new LocalViewUpdate(1, new ViewUpdate(View.of(2), View.of(0), View.of(0)));
        final long timeout = random.nextLong();
        when(timeoutCalculator.timeout(1)).thenReturn(timeout);

        localViewUpdateSenderWithTimeout.sendLocalViewUpdate(viewUpdate);
        verify(viewUpdateConsumer, times(1)).accept(viewUpdate);
        verify(pacemakerInfoSender, times(1)).sendCurrentView(View.of(2));
        verify(timeoutSender, times(1)).scheduleTimeout(View.of(2), timeout);
    }
}
