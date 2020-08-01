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

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.bft.View;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class LocalTimeoutTest {
	private LocalTimeout localTimeout;
	private long epoch;
	private View view;

	@Before
	public void setup() {
		epoch = 12345;
		view = mock(View.class);
		localTimeout = new LocalTimeout(epoch, view);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(LocalTimeout.class)
			.verify();
	}

	@Test
	public void testGetters() {
		assertEquals(this.view, this.localTimeout.getView());
		assertEquals(this.epoch, this.localTimeout.getEpoch());
	}
}