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
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.validators.ValidationState;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.UInt256;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PendingNewViewsTest {
	private PendingNewViews pendingNewViews;

	@Before
	public void setup() {
		this.pendingNewViews = new PendingNewViews(ECPublicKey::verify);
	}

	@Test
	public void when_inserting_newview_not_from_validator_set__no_qc_is_returned() {
		NewView newView = makeSignedNewViewFor(ECKeyPair.generateNew().getPublicKey(), View.genesis());
		NewView newView2 = makeSignedNewViewFor(ECKeyPair.generateNew().getPublicKey(), View.genesis());

		ValidatorSet validatorSet = ValidatorSet.from(Collections.singleton(Validator.from(newView.getAuthor(), UInt256.ONE)));

		assertThat(this.pendingNewViews.insertNewView(newView2, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_newview_with_invalid_signature__no_qc_is_returned() {
		ECPublicKey author = mock(ECPublicKey.class);
		when(author.verify(any(Hash.class), any())).thenReturn(false);
		NewView newView = makeSignedNewViewFor(author, View.genesis());

		ValidatorSet validatorSet = ValidatorSet.from(Collections.singleton(Validator.from(newView.getAuthor(), UInt256.ONE)));

		assertThat(this.pendingNewViews.insertNewView(newView, validatorSet)).isEmpty();
	}

	@Test
	public void when_inserting_newview_not_signed__exception_is_thrown() {
		NewView newView = makeUnsignedNewViewFor(ECKeyPair.generateNew().getPublicKey(), View.genesis());

		ValidatorSet validatorSet = ValidatorSet.from(Collections.singleton(Validator.from(newView.getAuthor(), UInt256.ONE)));

		assertThatThrownBy(() -> pendingNewViews.insertNewView(newView, validatorSet))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void when_inserting_valid_and_accepted_newview__qc_is_formed() {
		ECPublicKey author = mock(ECPublicKey.class);
		when(author.verify(any(Hash.class), any())).thenReturn(true);
		NewView newView = makeSignedNewViewFor(author, View.genesis());

		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		ECDSASignatures signatures = mock(ECDSASignatures.class);
		when(validationState.addSignature(any(), any())).thenReturn(true);
		when(validationState.complete()).thenReturn(true);
		when(validationState.signatures()).thenReturn(signatures);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsKey(any())).thenReturn(true);

		assertThat(this.pendingNewViews.insertNewView(newView, validatorSet)).contains(newView.getView());
	}

	@Test
	public void when_inserting_again__previous_newview_is_removed() {
		ECPublicKey author = mock(ECPublicKey.class);
		when(author.verify(any(Hash.class), any())).thenReturn(true);
		NewView newView = makeSignedNewViewFor(author, View.genesis());

		ValidatorSet validatorSet = mock(ValidatorSet.class);
		ValidationState validationState = mock(ValidationState.class);
		ECDSASignatures signatures = mock(ECDSASignatures.class);
		when(validationState.signatures()).thenReturn(signatures);
		when(validationState.isEmpty()).thenReturn(true);
		when(validatorSet.newValidationState()).thenReturn(validationState);
		when(validatorSet.containsKey(any())).thenReturn(true);

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

	private NewView makeUnsignedNewViewFor(ECPublicKey author, View view) {
		NewView newView = makeNewViewWithoutSignatureFor(author, view);
		when(newView.getSignature()).thenReturn(Optional.empty());
		return newView;
	}

	private NewView makeSignedNewViewFor(ECPublicKey author, View view) {
		NewView newView = makeNewViewWithoutSignatureFor(author, view);
		when(newView.getSignature()).thenReturn(Optional.of(new ECDSASignature()));
		return newView;
	}

	private NewView makeNewViewWithoutSignatureFor(ECPublicKey author, View view) {
		NewView newView = mock(NewView.class);
		when(newView.getAuthor()).thenReturn(author);
		when(newView.getView()).thenReturn(view);
		return newView;
	}
}
