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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.AccumulatorLocalSyncServiceProcessor.DtoCommandsAndProofVerifier;
import com.radixdlt.sync.AccumulatorLocalSyncServiceProcessor.DtoCommandsAndProofVerifierException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class AccumulatorRemoteSyncResponseVerifier implements RemoteSyncResponseProcessor {
	private static final Logger log = LogManager.getLogger();

	public interface VerifiedSyncedCommandsSender {
		void sendVerifiedCommands(VerifiedCommandsAndProof commandsAndProof);
	}

	public interface InvalidSyncedCommandsSender {
		void sendInvalidCommands(DtoCommandsAndProof commandsAndProof);
	}

	private final VerifiedSyncedCommandsSender verifiedSyncedCommandsSender;
	private final InvalidSyncedCommandsSender invalidSyncedCommandsSender;
	private final DtoCommandsAndProofVerifier verifier;

	@Inject
	public AccumulatorRemoteSyncResponseVerifier(
		VerifiedSyncedCommandsSender verifiedSyncedCommandsSender,
		InvalidSyncedCommandsSender invalidSyncedCommandsSender,
		DtoCommandsAndProofVerifier verifier
	) {
		this.verifiedSyncedCommandsSender = Objects.requireNonNull(verifiedSyncedCommandsSender);
		this.invalidSyncedCommandsSender = Objects.requireNonNull(invalidSyncedCommandsSender);
		this.verifier = Objects.requireNonNull(verifier);
	}


	@Override
	public void processSyncResponse(RemoteSyncResponse syncResponse) {
		log.info("SYNC_RESPONSE: {}", syncResponse);

		DtoCommandsAndProof commandsAndProof = syncResponse.getCommandsAndProof();

		VerifiedCommandsAndProof verified;
		try {
			verified = this.verifier.verify(commandsAndProof);
		} catch (DtoCommandsAndProofVerifierException e) {
			log.warn("SYNC_RESPONSE Verification failed {}: {}", commandsAndProof, e.getMessage());
			invalidSyncedCommandsSender.sendInvalidCommands(commandsAndProof);
			return;
		}

		// TODO: Check validity of response
		this.verifiedSyncedCommandsSender.sendVerifiedCommands(verified);
	}

}
