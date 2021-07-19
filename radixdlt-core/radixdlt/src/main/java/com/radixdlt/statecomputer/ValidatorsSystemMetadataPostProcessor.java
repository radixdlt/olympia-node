/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.engine.PostProcessor;
import com.radixdlt.engine.PostProcessorException;
import com.radixdlt.store.EngineStore;

import java.util.Arrays;
import java.util.List;

/**
 * Adds validatorsSystemMetadata at epoch boundary to result metadata.
 */
public final class ValidatorsSystemMetadataPostProcessor implements PostProcessor<LedgerAndBFTProof> {
	@Override
	public LedgerAndBFTProof process(
		LedgerAndBFTProof metadata,
		EngineStore<LedgerAndBFTProof> engineStore,
		List<REProcessedTxn> txns
	) throws PostProcessorException {
		if (metadata.getProof().getNextValidatorSet().isPresent()) {
			return metadata
				.withValidatorsSystemMetadata(getValidatorsSystemMetadata(engineStore, metadata));
		} else {
			return metadata;
		}
	}

	private ImmutableList<RawSubstateBytes> getValidatorsSystemMetadata(
		EngineStore<LedgerAndBFTProof> engineStore,
		LedgerAndBFTProof ledgerAndBFTProof
	) {
		final var validatorSet = ledgerAndBFTProof.getProof().getNextValidatorSet().orElseThrow();

		try (var validatorMetadataCursor = engineStore.openIndexedCursor(
			SubstateIndex.create(SubstateTypeId.VALIDATOR_SYSTEM_META_DATA.id(), ValidatorSystemMetadata.class))
		) {
			return Streams.stream(validatorMetadataCursor)
				.filter(rawSubstate -> {
					final var keyBytes = Arrays.copyOfRange(rawSubstate.getData(), 2, 2 + ECPublicKey.COMPRESSED_BYTES);
					try {
						final var key = ECPublicKey.fromBytes(keyBytes);
						return validatorSet.containsNode(key);
					} catch (PublicKeyException ex) {
						throw new RuntimeException(ex);
					}
				})
				.collect(ImmutableList.toImmutableList());
		}
	}
}
