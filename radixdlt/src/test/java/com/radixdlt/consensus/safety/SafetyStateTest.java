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

package com.radixdlt.consensus.safety;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.safety.SafetyState.Builder;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class SafetyStateTest {

	@Test
	public void when_build_with_no_new_views__then_should_return_the_same_object() {
		SafetyState safetyState = SafetyState.initialState();
		Builder builder = safetyState.toBuilder();
		assertThat(builder.build()).isSameAs(safetyState);
	}

	@Test
	public void when_build_with_new_last_voted_view__then_should_build_with_new_last_voted_view() {
		SafetyState safetyState = SafetyState.initialState();
		Builder builder = safetyState.toBuilder();
		View view = mock(View.class);
		builder.lastVotedView(view);
		SafetyState nextSafetyState = builder.build();
		assertThat(nextSafetyState.getLastVotedView()).isEqualTo(view);
		assertThat(nextSafetyState.getLockedView()).isEqualTo(safetyState.getLockedView());
		assertThat(nextSafetyState.getCommittedView()).isEqualTo(safetyState.getCommittedView());
		assertThat(nextSafetyState.getGenericQC()).isEqualTo(safetyState.getGenericQC());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(SafetyState.class).verify();
	}
}
