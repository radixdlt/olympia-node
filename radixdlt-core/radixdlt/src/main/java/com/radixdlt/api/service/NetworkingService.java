/* Copyright 2021 Radix DLT Ltd incorporated in England.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.network.p2p.addressbook.AddressBook;
import com.radixdlt.network.p2p.addressbook.AddressBookEntry;
import com.radixdlt.network.p2p.discovery.DiscoverPeers;
import com.radixdlt.networks.Addressing;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import static com.radixdlt.api.JsonRpcUtil.fromList;
import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public final class NetworkingService {
	@VisibleForTesting
	static final List<CounterType> NETWORKING_COUNTERS = List.of(
		CounterType.MESSAGES_INBOUND_RECEIVED,
		CounterType.MESSAGES_INBOUND_PROCESSED,
		CounterType.MESSAGES_INBOUND_DISCARDED,
		CounterType.MESSAGES_OUTBOUND_ABORTED,
		CounterType.MESSAGES_OUTBOUND_PENDING,
		CounterType.MESSAGES_OUTBOUND_PROCESSED,
		CounterType.MESSAGES_OUTBOUND_SENT,
		CounterType.NETWORKING_UDP_DROPPED_MESSAGES,
		CounterType.NETWORKING_TCP_DROPPED_MESSAGES,
		CounterType.NETWORKING_TCP_IN_OPENED,
		CounterType.NETWORKING_TCP_OUT_OPENED,
		CounterType.NETWORKING_TCP_CLOSED,
		CounterType.NETWORKING_SENT_BYTES,
		CounterType.NETWORKING_RECEIVED_BYTES
	);

	private final JSONObject configuration;

	private final SystemCounters systemCounters;
	private final PeersView peersView;
	private final AddressBook addressBook;
	private final Addressing addressing;
	private final EventDispatcher<DiscoverPeers> discoverPeersEventDispatcher;

	@Inject
	public NetworkingService(
		@Self ECPublicKey self,
		SystemCounters systemCounters,
		PeersView peersView,
		AddressBook addressBook,
		P2PConfig p2PConfig,
		Addressing addressing,
		EventDispatcher<DiscoverPeers> discoverPeersEventDispatcher
	) {
		this.systemCounters = systemCounters;
		this.peersView = peersView;
		this.addressBook = addressBook;
		this.addressing = addressing;
		this.discoverPeersEventDispatcher = discoverPeersEventDispatcher;

		configuration = prepareConfiguration(p2PConfig, self);
	}

	public JSONObject getConfiguration() {
		return configuration;
	}

	public JSONArray getPeers() {
		var peerArray = jsonArray();

		peersView.peers()
			.map(this::peerToJson)
			.forEach(peerArray::put);

		return peerArray;
	}

	public JSONArray getAddressBook() {
		final var entriesArray = jsonArray();
		addressBook.knownPeers().values().forEach(v -> entriesArray.put(addressBookEntryToJson(v)));
		return entriesArray;
	}

	public JSONObject getData() {
		return CountersJsonFormatter.countersToJson(systemCounters, NETWORKING_COUNTERS, false);
	}

	public long getPeersCount() {
		return peersView.peers().count();
	}

	private JSONObject prepareConfiguration(P2PConfig p2PConfig, ECPublicKey self) {
		return jsonObject()
			.put("defaultPort", p2PConfig.defaultPort())
			.put("discoveryInterval", p2PConfig.discoveryInterval())
			.put("listenAddress", p2PConfig.listenAddress())
			.put("listenPort", p2PConfig.listenPort())
			.put("broadcastPort", p2PConfig.broadcastPort())
			.put("peerConnectionTimeout", p2PConfig.peerConnectionTimeout())
			.put("maxInboundChannels", p2PConfig.maxInboundChannels())
			.put("maxOutboundChannels", p2PConfig.maxOutboundChannels())
			.put("channelBufferSize", p2PConfig.channelBufferSize())
			.put("peerLivenessCheckInterval", p2PConfig.peerLivenessCheckInterval())
			.put("pingTimeout", p2PConfig.pingTimeout())
			.put("seedNodes", fromList(p2PConfig.seedNodes(), seedNode -> seedNode))
			.put("nodeAddress", addressing.forNodes().of(self));
	}

	private JSONObject peerToJson(PeersView.PeerInfo peer) {
		var channelsJson = jsonArray();
		var peerJson = jsonObject().put("address", addressing.forNodes().of(peer.getNodeId().getPublicKey()));

		peer.getChannels().forEach(channel -> {
			var channelJson = jsonObject()
				.put("type", channel.isOutbound() ? "out" : "in")
				.put("localPort", channel.getSocketAddress().getPort())
				.put("ip", channel.getSocketAddress().getAddress().getHostAddress());

			channel.getUri().ifPresent(uri -> channelJson.put("uri", uri.toString()));
			channelsJson.put(channelJson);
		});
		peerJson.put("channels", channelsJson);
		return peerJson;
	}

	private JSONObject addressBookEntryToJson(AddressBookEntry e) {
		final var knownAddressesArray = jsonArray();

		e.getKnownAddresses().forEach(addr -> {
			final var addrObj = jsonObject()
				.put("uri", addr.getUri())
				.put("blacklisted", addr.blacklisted());
			addr.getLastSuccessfulConnection().ifPresent(ts -> addrObj.put("lastSuccessfulConnection", ts));
			knownAddressesArray.put(addrObj);
		});

		final var entryObj = jsonObject()
			.put("address", addressing.forNodes().of(e.getNodeId().getPublicKey()))
			.put("banned", e.isBanned())
			.put("knownAddresses", knownAddressesArray);

		e.bannedUntil().ifPresent(bannedUntil -> entryObj.put("bannedUntil", bannedUntil));

		return entryObj;
	}
}
