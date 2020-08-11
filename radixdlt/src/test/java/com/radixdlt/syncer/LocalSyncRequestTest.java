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

package com.radixdlt.syncer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.powermock.api.mockito.PowerMockito.mock;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.BFTNode;
import org.junit.Before;
import org.junit.Test;

public class LocalSyncRequestTest {
	private LocalSyncRequest request;
	private ImmutableList<BFTNode> target;

	@Before
	public void setup() {
		this.target = mock(ImmutableList.class);
		request = new LocalSyncRequest(3, 5, target);
	}

	@Test
	public void testGetters() {
		assertThat(request.getTargetVersion()).isEqualTo(3);
		assertThat(request.getCurrentVersion()).isEqualTo(5);
		assertThat(request.getTarget()).isEqualTo(target);
	}
}