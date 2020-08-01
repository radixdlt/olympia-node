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

package com.radixdlt.middleware2;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochView;
import org.junit.Test;

public class InMemoryEpochInfoTest {
	@Test
	public void when_send_current_view_and_get_view__then_returns_sent_view() {
		InMemoryEpochInfo inMemoryEpochInfo = new InMemoryEpochInfo();
		EpochView currentView = mock(EpochView.class);

		inMemoryEpochInfo.sendCurrentView(currentView);
		assertThat(inMemoryEpochInfo.getCurrentView()).isEqualTo(currentView);
	}

	@Test
	public void when_send_timeout_view_and_get_timeout_view__then_returns_sent_timeout_view() {
		InMemoryEpochInfo inMemoryEpochInfo = new InMemoryEpochInfo();
		EpochView timeoutView = mock(EpochView.class);
		BFTNode leader = mock(BFTNode.class);

		inMemoryEpochInfo.sendTimeoutProcessed(timeoutView, leader);
		assertThat(inMemoryEpochInfo.getLastTimeout()).isEqualTo(timeoutView);
	}
}