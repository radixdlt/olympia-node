/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.api.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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

import java.util.Map;
import java.util.Objects;

import static java.util.function.Predicate.not;

/**
 * Collects other peers' latest known forks hashes (received during the handshake) and compares it against
 * local list of forks. Signals with a flag, when there's a known fork on any of the validator nodes
 * that local node is not aware of, which means that there's potentially a newer app version to download.
 */
@Singleton
public final class PeersForksHashesInfoService {
	private final Forks forks;
	private final Addressing addressing;

	private BFTValidatorSet currentValidatorSet;
	private ImmutableMap<HashCode, ImmutableSet<ECPublicKey>> unknownReportedForksHashes;

	@Inject
	public PeersForksHashesInfoService(
		Forks forks,
		Addressing addressing,
		EpochChange initialEpoch
	) {
		this.forks = Objects.requireNonNull(forks);
		this.addressing = Objects.requireNonNull(addressing);

		this.currentValidatorSet = initialEpoch.getBFTConfiguration().getValidatorSet();
		this.unknownReportedForksHashes = ImmutableMap.of();
	}

	public EventProcessor<PeerEvent> peerEventProcessor() {
		return peerEvent -> {
			if (peerEvent instanceof PeerEvent.PeerConnected) {
				final var peerChannel = ((PeerEvent.PeerConnected) peerEvent).getChannel();
				final var peerPubKey = peerChannel.getRemoteNodeId().getPublicKey();
				final var peerLatestKnownForkHash = peerChannel.getRemoteLatestKnownForkHash();
				final var isPeerForkHashKnown = forks.getByHash(peerLatestKnownForkHash).isPresent();
				final var peerIsInValidatorSet = currentValidatorSet.containsNode(BFTNode.create(peerPubKey));
				if (peerIsInValidatorSet && !isPeerForkHashKnown) {
					addUnknownReportedForkHash(peerPubKey, peerLatestKnownForkHash);
				}
			}
		};
	}

	private void addUnknownReportedForkHash(ECPublicKey publicKey, HashCode forkHash) {
		final var currentReportsForHash =
			this.unknownReportedForksHashes.getOrDefault(forkHash, ImmutableSet.of());

		final var newReportsForHash = ImmutableSet.<ECPublicKey>builder()
			.addAll(currentReportsForHash)
			.add(publicKey)
			.build();

		this.unknownReportedForksHashes = ImmutableMap.<HashCode, ImmutableSet<ECPublicKey>>builder()
			.putAll(
				this.unknownReportedForksHashes.entrySet().stream()
					.filter(not(e -> e.getKey().equals(forkHash)))
					.collect(ImmutableSet.toImmutableSet())
			)
			.put(Map.entry(forkHash, newReportsForHash))
			.build();
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
