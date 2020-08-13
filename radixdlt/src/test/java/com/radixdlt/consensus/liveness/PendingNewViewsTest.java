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

import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.ValidationState;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.utils.UInt256;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PendingNewViewsTest {
	private PendingNewViews pendingNewViews;

	@Before
	public void setup() {
		this.pendingNewViews = new PendingNewViews();
	}

	@Test
	public void when_inserting_newview_not_from_validator_set__no_qc_is_returned() {
		NewView newView = makeSignedNewViewFor(mock(BFTNode.class), View.genesis());
		NewView newView2 = makeSignedNewViewFor(mock(BFTNode.class), View.genesis());

		BFTValidatorSet validatorSet = BFTValidatorSet.from(
			Collections.singleton(BFTValidator.from(newView.getAuthor(), UInt256.ONE))
		);

		assertThat(this.pendingNewViews.insertNewView(newView2, validatorSet)).isEmpty();
	}


	@Test
	public void when_inserting_newview_not_signed__exception_is_thrown() {
		NewView newView = makeUnsignedNewViewFor(mock(BFTNode.class), View.genesis());

		BFTValidatorSet validatorSet = BFTValidatorSet.from(
			Collections.singleton(BFTValidator.from(newView.getAuthor(), UInt256.ONE))
		);

		assertThatThrownBy(() -> pendingNewViews.insertNewView(newView, validatorSet))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void when_inserting_valid_and_accepted_newview__qc_is_formed() {
		BFTNode author = mock(BFTNode.class);
		NewView newView = makeSignedNewViewFor(author, View.genesis());

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		TimestampedECDSASignatures signatures = mock(TimestampedECDSASignatures.class);
		when(validationState.addSignature(any(), anyLong(), any())).thenReturn(true);
		when(validationState.complete()).thenReturn(true);
		when(validationState.signatures()).thenReturn(signatures);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsNode(any())).thenReturn(true);

		assertThat(this.pendingNewViews.insertNewView(newView, validatorSet)).contains(newView.getView());
	}

	@Test
	public void when_inserting_again__previous_newview_is_removed() {
		BFTNode author = mock(BFTNode.class);
		NewView newView = makeSignedNewViewFor(author, View.genesis());

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		TimestampedECDSASignatures signatures = mock(TimestampedECDSASignatures.class);
		when(validationState.signatures()).thenReturn(signatures);
		when(validationState.isEmpty()).thenReturn(true);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsNode(any())).thenReturn(true);

		// Preconditions
		assertThat(this.pendingNewViews.insertNewView(newView, validatorSet)).isNotPresent();
		assertEquals(1, this.pendingNewViews.newViewStateSize());
		assertEquals(1, this.pendingNewViews.previousNewViewsSize());

		NewView newView2 = makeSignedNewViewFor(author, View.of(1));

		// Should not change size with different new view for same author
		assertThat(this.pendingNewViews.insertNewView(newView2, validatorSet)).isNotPresent();
		assertEquals(1, this.pendingNewViews.newViewStateSize());
		assertEquals(1, this.pendingNewViews.previousNewViewsSize());

		// Should not change size with repeat new view
		assertThat(this.pendingNewViews.insertNewView(newView2, validatorSet)).isNotPresent();
		assertEquals(1, this.pendingNewViews.newViewStateSize());
		assertEquals(1, this.pendingNewViews.previousNewViewsSize());
	}

	private NewView makeUnsignedNewViewFor(BFTNode author, View view) {
		NewView newView = makeNewViewWithoutSignatureFor(author, view);
		when(newView.getSignature()).thenReturn(Optional.empty());
		return newView;
	}

	private NewView makeSignedNewViewFor(BFTNode author, View view) {
		NewView newView = makeNewViewWithoutSignatureFor(author, view);
		when(newView.getSignature()).thenReturn(Optional.of(new ECDSASignature()));
		return newView;
	}

	private NewView makeNewViewWithoutSignatureFor(BFTNode author, View view) {
		NewView newView = mock(NewView.class);
		when(newView.getAuthor()).thenReturn(author);
		when(newView.getView()).thenReturn(view);
		return newView;
	}
}
