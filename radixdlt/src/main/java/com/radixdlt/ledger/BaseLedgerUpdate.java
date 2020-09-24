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

package com.radixdlt.ledger;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import java.util.Objects;
import java.util.Optional;

public final class BaseLedgerUpdate implements LedgerUpdate {
	private final VerifiedCommandsAndProof verifiedCommandsAndProof;
	private final BFTValidatorSet validatorSet;

	public BaseLedgerUpdate(VerifiedCommandsAndProof verifiedCommandsAndProof, BFTValidatorSet validatorSet) {
		this.verifiedCommandsAndProof = Objects.requireNonNull(verifiedCommandsAndProof);
		this.validatorSet = validatorSet;
	}

	@Override
	public ImmutableList<Command> getNewCommands() {
		return verifiedCommandsAndProof.getCommands();
	}

	@Override
	public VerifiedLedgerHeaderAndProof getTail() {
		return verifiedCommandsAndProof.getHeader();
	}

	@Override
	public Optional<BFTValidatorSet> getNextValidatorSet() {
		return Optional.ofNullable(validatorSet);
	}
}
