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

package com.radixdlt.sync;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.ledger.VerifiedTxnsAndProof;

import java.util.Optional;

/**
 * Reader of committed commands
 */
public interface CommittedReader {
	VerifiedTxnsAndProof getNextCommittedTxns(DtoLedgerProof start);
	Optional<LedgerProof> getEpochProof(long epoch);
	Optional<LedgerProof> getLastProof();
	ImmutableMap<Long, HashCode> getEpochsForkHashes();

	static CommittedReader mocked() {
		return new CommittedReader() {
			@Override
			public VerifiedTxnsAndProof getNextCommittedTxns(DtoLedgerProof start) {
				return null;
			}

			@Override
			public Optional<LedgerProof> getEpochProof(long epoch) {
				return Optional.empty();
			}

			@Override
			public Optional<LedgerProof> getLastProof() {
				return Optional.empty();
			}

			@Override
			public ImmutableMap<Long, HashCode> getEpochsForkHashes() {
				return ImmutableMap.of();
			}
		};
	}
}
