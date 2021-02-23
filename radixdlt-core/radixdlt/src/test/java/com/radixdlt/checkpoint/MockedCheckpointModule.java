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

package com.radixdlt.checkpoint;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.UInt256;
import org.radix.TokenIssuance;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class MockedCheckpointModule extends AbstractModule {
	private static final ImmutableMap<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> TOKEN_PERMISSIONS =
		ImmutableMap.of(
			MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
			MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
		);

	@Override
	public void configure() {
		bindConstant().annotatedWith(Names.named("magic")).to(0);
		Multibinder.newSetBinder(binder(), TokenIssuance.class);
	}

	@Provides
	@Singleton
	@NativeToken
	private RRI nativeToken(
		@Named("magic") int magic,
		@Named("universeKey") ECKeyPair universeKey // TODO: Remove universe key
	) {
		return RRI.of(new RadixAddress((byte) magic, universeKey.getPublicKey()), "NOSUCHTOKEN");
	}

	@Provides
	public ImmutableList<TokenIssuance> tokenIssuanceList(Set<TokenIssuance> tokenIssuanceSet) {
		return tokenIssuanceSet.stream()
			.sorted(Comparator.comparing(t -> t.receiver().toBase58()))
			.collect(ImmutableList.toImmutableList());
	}

	@Provides
	VerifiedCommandsAndProof genesis(
		@Named("magic") int magic,
		@Named("universeKey") ECKeyPair universeKey, // TODO: Remove universe key
		ImmutableList<TokenIssuance> tokenIssuances,
		List<BFTNode> initialValidators,
		Serialization serialization,
		Hasher hasher
	) {
		BFTValidatorSet validatorSet = BFTValidatorSet.from(initialValidators.stream().map(node -> BFTValidator.from(node, UInt256.ONE)));

		Atom atom = new Atom();
		atom.addParticleGroup(ParticleGroup.of(
			CheckpointUtils.createTokenDefinition(
				(byte) magic,
				universeKey.getPublicKey(),
				"NOSUCHTOKEN",
				"Test",
				"Testing Token",
				"",
				"",
				TOKEN_PERMISSIONS,
				UInt256.ZERO,
				tokenIssuances
			)
		));
		atom.addParticleGroup(ParticleGroup.of(CheckpointUtils.createEpochUpdate()));
		atom.sign(universeKey, hasher);

		final ClientAtom genesisAtom = ClientAtom.convertFromApiAtom(atom, hasher);
		byte[] payload = serialization.toDson(genesisAtom, Output.ALL);
		Command command = new Command(payload);
		// Checkpoint
		VerifiedLedgerHeaderAndProof genesisLedgerHeader = VerifiedLedgerHeaderAndProof.genesis(
			HashUtils.zero256(),
			validatorSet
		);
		return new VerifiedCommandsAndProof(
			ImmutableList.of(command),
			genesisLedgerHeader
		);
	}
}
