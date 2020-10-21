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

package com.radixdlt.consensus.liveness;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.utils.UInt256;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PendingViewTimeoutsTest {
	private PendingViewTimeouts pendingViewTimeouts;

	@Before
	public void setup() {
		this.pendingViewTimeouts = new PendingViewTimeouts();
	}

	@Test
	public void when_inserting_view_timeout_not_from_validator_set__no_qc_is_returned() {
		ViewTimeout viewTimeout = makeSignedViewTimeoutFor(mock(BFTNode.class), View.genesis());
		ViewTimeout viewTimeout2 = makeSignedViewTimeoutFor(mock(BFTNode.class), View.genesis());

		BFTValidatorSet validatorSet = BFTValidatorSet.from(
			Collections.singleton(BFTValidator.from(viewTimeout.getAuthor(), UInt256.ONE))
		);

		assertThat(this.pendingViewTimeouts.insertViewTimeout(viewTimeout2, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_valid_and_accepted_view_timeout__qc_is_formed() {
		BFTNode author = mock(BFTNode.class);
		ViewTimeout viewTimeout = makeSignedViewTimeoutFor(author, View.genesis());

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		TimestampedECDSASignatures signatures = mock(TimestampedECDSASignatures.class);
		when(validationState.addSignature(any(), anyLong(), any())).thenReturn(true);
		when(validationState.complete()).thenReturn(true);
		when(validationState.signatures()).thenReturn(signatures);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsNode(any())).thenReturn(true);

		assertThat(this.pendingViewTimeouts.insertViewTimeout(viewTimeout, validatorSet)).contains(viewTimeout.getView());
	}

	@Test
	public void when_inserting_again__previous_view_timeout_is_removed() {
		BFTNode author = mock(BFTNode.class);
		ViewTimeout viewTimeout = makeSignedViewTimeoutFor(author, View.genesis());

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		TimestampedECDSASignatures signatures = mock(TimestampedECDSASignatures.class);
		when(validationState.signatures()).thenReturn(signatures);
		when(validationState.isEmpty()).thenReturn(true);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsNode(any())).thenReturn(true);

		// Preconditions
		assertThat(this.pendingViewTimeouts.insertViewTimeout(viewTimeout, validatorSet)).isNotPresent();
		assertEquals(1, this.pendingViewTimeouts.viewTimeoutStateSize());
		assertEquals(1, this.pendingViewTimeouts.previousViewTimeoutSize());

		ViewTimeout viewTimeout2 = makeSignedViewTimeoutFor(author, View.of(1));

		// Should not change size with different view timeout for same author
		assertThat(this.pendingViewTimeouts.insertViewTimeout(viewTimeout2, validatorSet)).isNotPresent();
		assertEquals(1, this.pendingViewTimeouts.viewTimeoutStateSize());
		assertEquals(1, this.pendingViewTimeouts.previousViewTimeoutSize());

		// Should not change size with repeat view timeout
		assertThat(this.pendingViewTimeouts.insertViewTimeout(viewTimeout2, validatorSet)).isNotPresent();
		assertEquals(1, this.pendingViewTimeouts.viewTimeoutStateSize());
		assertEquals(1, this.pendingViewTimeouts.previousViewTimeoutSize());
	}

	private ViewTimeout makeSignedViewTimeoutFor(BFTNode author, View view) {
		ViewTimeout viewTimeout = makeViewTimeoutWithoutSignatureFor(author, view);
		when(viewTimeout.signature()).thenReturn(new ECDSASignature());
		return viewTimeout;
	}

	private ViewTimeout makeViewTimeoutWithoutSignatureFor(BFTNode author, View view) {
		ViewTimeout viewTimeout = mock(ViewTimeout.class);
		when(viewTimeout.getAuthor()).thenReturn(author);
		when(viewTimeout.getView()).thenReturn(view);
		return viewTimeout;
	}
}
