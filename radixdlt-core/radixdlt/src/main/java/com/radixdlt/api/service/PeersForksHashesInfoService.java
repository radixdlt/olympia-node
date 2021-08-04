/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.service;

import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.forks.Forks;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Collects other peers' latest known forks hashes (received during the handshake) and compares it against
 * local list of forks. Signals with a flag, when there's a known fork on any of the validator nodes
 * that local node is not aware of, which means that there's potentially a newer app version to download.
 */
public final class PeersForksHashesInfoService {
	// max number of map entries (fork hashes)
	private static final int MAX_FORK_HASHES_KEYS = 50;

	// max number of reports (peers' public keys) per fork hash
	private static final int MAX_REPORTS_PER_HASH = 20;

	private final Forks forks;
	private final Addressing addressing;

	private BFTValidatorSet currentValidatorSet;
	private final LinkedHashMap<HashCode, ImmutableSet<ECPublicKey>> unknownReportedForksHashes;

	@Inject
	public PeersForksHashesInfoService(
		Forks forks,
		Addressing addressing,
		EpochChange initialEpoch
	) {
		this.forks = Objects.requireNonNull(forks);
		this.addressing = Objects.requireNonNull(addressing);

		this.currentValidatorSet = initialEpoch.getBFTConfiguration().getValidatorSet();
		this.unknownReportedForksHashes = new LinkedHashMap<>() {
			@Override
			protected boolean removeEldestEntry(final Map.Entry eldest) {
				return size() > MAX_FORK_HASHES_KEYS;
			}
		};
	}

	public EventProcessor<PeerEvent> peerEventProcessor() {
		return peerEvent -> {
			if (peerEvent instanceof PeerEvent.PeerConnected) {
				final var peerChannel = ((PeerEvent.PeerConnected) peerEvent).getChannel();
				peerChannel.getRemoteLatestKnownForkHash().ifPresent(peerLatestKnownForkHash -> {
					final var peerPubKey = peerChannel.getRemoteNodeId().getPublicKey();
					final var isPeerForkHashKnown = forks.getByHash(peerLatestKnownForkHash).isPresent();
					final var peerIsInValidatorSet = currentValidatorSet.containsNode(BFTNode.create(peerPubKey));
					if (peerIsInValidatorSet && !isPeerForkHashKnown) {
						addUnknownReportedForkHash(peerPubKey, peerLatestKnownForkHash);
					}
				});
			}
		};
	}

	private void addUnknownReportedForkHash(ECPublicKey publicKey, HashCode forkHash) {
		final var currentReportsForHash =
			this.unknownReportedForksHashes.getOrDefault(forkHash, ImmutableSet.of());

		if (currentReportsForHash.size() < MAX_REPORTS_PER_HASH) {
			final var newReportsForHash = ImmutableSet.<ECPublicKey>builder()
				.addAll(currentReportsForHash)
				.add(publicKey)
				.build();

			this.unknownReportedForksHashes.put(forkHash, newReportsForHash);
		}
	}

	@SuppressWarnings("unchecked")
	public EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor() {
		return ledgerUpdate -> {
			final var epochChange = ledgerUpdate.getStateComputerOutput().getInstance(EpochChange.class);
			if (epochChange != null) {
				this.currentValidatorSet = epochChange.getBFTConfiguration().getValidatorSet();
			}
		};
	}

	public JSONObject getUnknownReportedForksHashes() {
		final var jsonObj = new JSONObject();

		this.unknownReportedForksHashes.forEach((forkHash, reportedBy) -> {
			final var reportedByArray = new JSONArray();
			reportedBy.forEach(pubKey -> reportedByArray.put(addressing.forValidators().of(pubKey)));
			jsonObj.put(forkHash.toString(), reportedByArray);
		});

		return jsonObj;
	}
}
