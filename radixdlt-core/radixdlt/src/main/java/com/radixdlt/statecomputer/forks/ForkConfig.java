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

package com.radixdlt.statecomputer.forks;

import com.google.common.hash.HashCode;
import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachineConfig;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.BatchVerifier;
import com.radixdlt.engine.PostProcessedVerifier;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.Triplet;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

/**
 * Configuration used for hard forks
 */
public final class ForkConfig {
	private final String name;
	private final Predicate<Triplet<ForkConfig, RadixEngine<LedgerAndBFTProof>, LedgerAndBFTProof>> executePredicate;
	private final REParser parser;
	private final SubstateSerialization serialization;
	private final ConstraintMachineConfig constraintMachineConfig;
	private final ActionConstructors actionConstructors;
	private final BatchVerifier<LedgerAndBFTProof> batchVerifier;
	private final PostProcessedVerifier postProcessedVerifier;
	private final View epochCeilingView;

	public ForkConfig(
		String name,
		Predicate<Triplet<ForkConfig, RadixEngine<LedgerAndBFTProof>, LedgerAndBFTProof>> executePredicate,
		REParser parser,
		SubstateSerialization serialization,
		ConstraintMachineConfig constraintMachineConfig,
		ActionConstructors actionConstructors,
		BatchVerifier<LedgerAndBFTProof> batchVerifier,
		PostProcessedVerifier postProcessedVerifier,
		View epochCeilingView
	) {
		this.name = name;
		this.executePredicate = executePredicate;
		this.parser = parser;
		this.serialization = serialization;
		this.constraintMachineConfig = constraintMachineConfig;
		this.actionConstructors = actionConstructors;
		this.batchVerifier = batchVerifier;
		this.postProcessedVerifier = postProcessedVerifier;
		this.epochCeilingView = epochCeilingView;
	}

	public String getName() {
		return name;
	}

	public Predicate<Triplet<ForkConfig, RadixEngine<LedgerAndBFTProof>, LedgerAndBFTProof>> getExecutePredicate() {
		return this.executePredicate;
	}

	public REParser getParser() {
		return parser;
	}

	public SubstateSerialization getSubstateSerialization() {
		return serialization;
	}

	public ConstraintMachineConfig getConstraintMachineConfig() {
		return constraintMachineConfig;
	}

	public ActionConstructors getActionConstructors() {
		return actionConstructors;
	}

	public BatchVerifier<LedgerAndBFTProof> getBatchVerifier() {
		return batchVerifier;
	}

	public PostProcessedVerifier getPostProcessedVerifier() {
		return postProcessedVerifier;
	}

	public View getEpochCeilingView() {
		return epochCeilingView;
	}

	public HashCode getHash() {
		return HashUtils.sha256(name.getBytes(StandardCharsets.UTF_8));
	}

	public static HashCode voteHash(ECPublicKey publicKey, ForkConfig forkConfig) {
		return voteHash(publicKey, forkConfig.getHash());
	}

	public static HashCode voteHash(ECPublicKey publicKey, HashCode forkHash) {
		final var bytes = ByteUtils.concatenate(publicKey.getBytes(), forkHash.asBytes());
		return HashUtils.sha256(bytes);
	}
}
