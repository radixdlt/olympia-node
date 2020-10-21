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

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.LedgerAccumulatorVerifier;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RemoteSyncResponseAccumulatorVerifier implements RemoteSyncResponseProcessor {
	private static final Logger log = LogManager.getLogger();

	public interface VerifiedAccumulatorSender {
		void sendVerifiedAccumulator(RemoteSyncResponse remoteSyncResponse);
	}

	public interface InvalidAccumulatorSender {
		void sendInvalidAccumulator(RemoteSyncResponse remoteSyncResponse);
	}

	private final VerifiedAccumulatorSender verifiedSender;
	private final InvalidAccumulatorSender invalidSyncedCommandsSender;
	private final LedgerAccumulatorVerifier accumulatorVerifier;
	private final Hasher hasher;

	@Inject
	public RemoteSyncResponseAccumulatorVerifier(
		VerifiedAccumulatorSender verifiedSender,
		InvalidAccumulatorSender invalidSyncedCommandsSender,
		LedgerAccumulatorVerifier accumulatorVerifier,
		Hasher hasher
	) {
		this.verifiedSender = Objects.requireNonNull(verifiedSender);
		this.invalidSyncedCommandsSender = Objects.requireNonNull(invalidSyncedCommandsSender);
		this.accumulatorVerifier = Objects.requireNonNull(accumulatorVerifier);
		this.hasher = Objects.requireNonNull(hasher);
	}

	@Override
	public void processSyncResponse(RemoteSyncResponse syncResponse) {
		log.info("SYNC_RESPONSE: Accumulator verifier {}", syncResponse);

		DtoCommandsAndProof commandsAndProof = syncResponse.getCommandsAndProof();

		AccumulatorState start = commandsAndProof.getHead().getLedgerHeader().getAccumulatorState();
		AccumulatorState end = commandsAndProof.getTail().getLedgerHeader().getAccumulatorState();
		ImmutableList<HashCode> hashes = commandsAndProof.getCommands().stream()
			.map(hasher::hash)
			.collect(ImmutableList.toImmutableList());
		if (!this.accumulatorVerifier.verify(start, hashes, end)) {
			invalidSyncedCommandsSender.sendInvalidAccumulator(syncResponse);
			return;
		}

		// TODO: Check validity of response
		this.verifiedSender.sendVerifiedAccumulator(syncResponse);
	}

}
