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

package com.radixdlt.consensus.bft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.SyncInfo;

import org.junit.Before;
import org.junit.Test;

public class NewViewSignerTest {
	private NewViewSigner newViewSigner;
	private BFTNode self = mock(BFTNode.class);
	private HashSigner hashSigner = mock(HashSigner.class);

	@Before
	public void setup() {
		newViewSigner = new NewViewSigner(self, hashSigner);
	}

	@Test
	public void when_create_new_view__then_should_be_correct() {
		View view = mock(View.class);
		when(view.number()).thenReturn(1L);

		NewView newView = newViewSigner.signNewView(view, mock(SyncInfo.class));

		assertThat(newView.getAuthor()).isEqualTo(self);
	}
}