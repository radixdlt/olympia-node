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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.bft.View;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class EpochViewTest {

	@Test
	public void testBadArgument() {
		assertThatThrownBy(() -> EpochView.of(-1, View.of(1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testGetters() {
		View view = mock(View.class);
		EpochView epochView = EpochView.of(12345L, view);
		assertThat(epochView.getEpoch()).isEqualTo(12345L);
		assertThat(epochView.getView()).isEqualTo(view);
	}

	@Test
	public void testToString() {
		View view = mock(View.class);
		EpochView epochView = EpochView.of(12345L, view);
		assertThat(epochView.toString()).isNotNull();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(EpochView.class)
			.verify();
	}
}