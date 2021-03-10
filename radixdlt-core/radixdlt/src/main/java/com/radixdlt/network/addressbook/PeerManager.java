/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.network.addressbook;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageFromPeer;
import com.radixdlt.network.transport.TransportException;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.utils.ThreadFactories;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.discovery.BootstrapDiscovery;
import org.radix.network.discovery.Whitelist;
import org.radix.network.messages.GetPeersMessage;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.PeerPongMessage;
import org.radix.network.messages.PeersMessage;
import org.radix.network.messaging.Message;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.SystemMessage;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PeerManager {
	private static final Logger log = LogManager.getLogger();

	private final Random rand = new Random(); // No need for cryptographically secure here
	private final Map<Peer, Long> probes = new HashMap<>();

	private final AddressBook addressbook;
	private final MessageCentral messageCentral;
	private final BootstrapDiscovery bootstrapDiscovery;

	private final long peersBroadcastIntervalMs;
	private final long peersBroadcastDelayMs;
	private final long peerProbeIntervalMs;
	private final long peerProbeDelayMs;

	private final long peerProbeTimeoutMs;
	private final long peerProbeFrequencyMs;

	private final long heartbeatPeersIntervalMs;
	private final long heartbeatPeersDelayMs;

	private final long discoverPeersIntervalMs;
	private final long discoverPeersDelayMs;

	private final int peerMessageBatchSize;

	private final long recencyThreshold;
	private final int universeMagic;

	private final SecureRandom rng;
	private final Whitelist whitelist;
	private final LocalSystem localSystem;

	private ScheduledExecutorService executor; // Ideally would be injected at some point
	private Scheduler scheduler;
	private Disposable disposable;

	private final Object startedLock = new Object();
	private boolean started = false;

	private class ProbeTask implements Runnable {
		private LinkedList<PeerWithSystem> peersToProbe = new LinkedList<>();
		private int numPeers = 0;

		@Override
		public void run() {
			try {
				long recencyInSeconds = TimeUnit.MILLISECONDS.toSeconds(recencyThreshold);
				int numProbes = (int) (this.numPeers / Math.max(1, recencyInSeconds));

				numProbes = Math.max(numProbes, 16);

				if (peersToProbe.isEmpty()) {
					addressbook.peers()
						.filter(StandardFilters.standardFilter(localSystem.getNID(), whitelist))
						.forEachOrdered(peersToProbe::add);
					this.numPeers = peersToProbe.size();
				}

				numProbes = Math.min(numProbes, peersToProbe.size());
				if (numProbes > 0) {
					List<PeerWithSystem> toProbe = peersToProbe.subList(0, numProbes);
					toProbe.forEach(PeerManager.this::probe);
					toProbe.clear();
				}
			} catch (Exception ex) {
				log.error("Peer probing failed", ex);
			}
		}
	}

	@Inject
	PeerManager(
		PeerManagerConfiguration config,
		AddressBook addressbook,
		MessageCentral messageCentral,
		BootstrapDiscovery bootstrapDiscovery,
		SecureRandom rng,
		LocalSystem localSystem,
		RuntimeProperties properties,
		@Named("magic") int magic
	) {
		super();

		this.addressbook = Objects.requireNonNull(addressbook);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.bootstrapDiscovery = Objects.requireNonNull(bootstrapDiscovery);
		this.rng = Objects.requireNonNull(rng);
		this.localSystem = localSystem;
		this.recencyThreshold = properties.get("network.peers.recency_ms", 60L * 1000L);
		this.whitelist = Whitelist.from(properties);
		this.universeMagic = magic;

		this.peersBroadcastIntervalMs = config.networkPeersBroadcastInterval(30000);
		this.peersBroadcastDelayMs = config.networkPeersBroadcastDelay(60000);

		this.peerProbeIntervalMs = config.networkPeersProbeInterval(1000);
		this.peerProbeDelayMs = config.networkPeersProbeDelay(0);
		this.peerProbeFrequencyMs = config.networkPeersProbeFrequency(30000);
		this.peerProbeTimeoutMs = config.networkPeersProbeTimeout(20000);

		this.heartbeatPeersIntervalMs = config.networkHeartbeatPeersInterval(10000);
		this.heartbeatPeersDelayMs = config.networkHeartbeatPeersDelay(10000);

		this.discoverPeersIntervalMs = config.networkDiscoverPeersInterval(10000);
		this.discoverPeersDelayMs = config.networkDiscoverPeersDelay(1000);

		this.peerMessageBatchSize = config.networkPeersMessageBatchSize(64);

		log.info("{} initialised, "
			+ "peersBroadcastInterval={}, peersBroadcastDelay={}, peersProbeInterval={}, "
			+ "peersProbeDelay={}, heartbeatPeersInterval={}, heartbeatPeersDelay={}, "
			+ "discoverPeersInterval={}, discoverPeersDelay={}, peerProbeFrequency={}",
			this.getClass().getSimpleName(),
			this.peersBroadcastIntervalMs, this.peersBroadcastDelayMs, this.peerProbeIntervalMs,
			this.peerProbeDelayMs, this.heartbeatPeersIntervalMs, this.heartbeatPeersDelayMs,
			this.discoverPeersIntervalMs, this.discoverPeersDelayMs, this.peerProbeFrequencyMs
		);
	}

	public void start() {
		synchronized (this.startedLock) {
			if (!this.started) {
				// Listen for messages

				this.executor = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("PeerManager"));
				this.scheduler = Schedulers.from(executor);

				final var disposables = List.of(
					subscribe(PeersMessage.class, m -> this.handlePeersMessage(m.getPeer(), m.getMessage())),
					subscribe(GetPeersMessage.class, m -> this.handleGetPeersMessage(m.getPeer(), m.getMessage())),
					subscribe(PeerPingMessage.class, m -> this.handlePeerPingMessage(m.getPeer(), m.getMessage())),
					subscribe(PeerPongMessage.class, m -> this.handlePeerPongMessage(m.getPeer(), m.getMessage())),
					subscribe(SystemMessage.class, m -> this.handleHeartbeatPeersMessage(m.getPeer(), m.getMessage()))
				);

				this.disposable = new CompositeDisposable(disposables);

				// Tasks
				this.executor.scheduleAtFixedRate(
					this::heartbeatPeers,
					heartbeatPeersDelayMs,
					heartbeatPeersIntervalMs,
					TimeUnit.MILLISECONDS
				);
				this.executor.scheduleWithFixedDelay(
					this::peersHousekeeping,
					peersBroadcastDelayMs,
					peersBroadcastIntervalMs,
					TimeUnit.MILLISECONDS
				);
				this.executor.scheduleWithFixedDelay(
					new ProbeTask(),
					peerProbeDelayMs,
					peerProbeIntervalMs,
					TimeUnit.MILLISECONDS
				);
				this.executor.scheduleWithFixedDelay(
					this::discoverPeers,
					discoverPeersDelayMs,
					discoverPeersIntervalMs,
					TimeUnit.MILLISECONDS
				);

				log.info("PeerManager started");
				this.started = true;
			}
		}
	}

	private <T extends Message> Disposable subscribe(Class<T> clazz, Consumer<MessageFromPeer<T>> consumer) {
		return this.messageCentral.messagesOf(clazz)
			.observeOn(this.scheduler)
			.subscribe(consumer);
	}

	public void stop() {
		synchronized (this.startedLock) {
			if (this.started) {
				this.disposable.dispose();
				this.executor.shutdownNow();
				try {
					this.executor.awaitTermination(10L, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					// Not going to deal with this here
					Thread.currentThread().interrupt();
				}
				this.executor = null;

				log.info("PeerManager stopped");
				this.started = false;
			}
		}
	}

	private void heartbeatPeers() {
		// System Heartbeat
		SystemMessage msg = new SystemMessage(localSystem, this.universeMagic);
		addressbook.recentPeers().forEachOrdered(peer -> {
			try {
				messageCentral.send(peer, msg);
			} catch (TransportException ioex) {
				log.error(String.format("Could not send System heartbeat to %s", peer), ioex);
			}
		});
	}

	private void handleHeartbeatPeersMessage(Peer peer, SystemMessage heartBeatMessage) {
		log.debug("Received SystemMessage from {}", peer);
	}

	private void handlePeersMessage(Peer peer, PeersMessage peersMessage) {
		log.debug("Received PeersMessage from {}", peer);
		List<PeerWithSystem> peers = peersMessage.getPeers();
		if (peers != null) {
			List<PeerWithSystem> unknownPeers = peers.stream()
				.filter(PeerWithSystem::hasTransports)
				.filter(this::notOurNid)
				.filter(this::notInAddressBook)
				.collect(Collectors.toList());
			if (!unknownPeers.isEmpty()) {
				// Only nudge one peer to avoid amplification attacks
				probe(unknownPeers.get(this.rand.nextInt(unknownPeers.size())));
			}
		}
	}

	//TODO: incoming message is completely ignored. Perhaps we need to at least validate its universe.
	private void handleGetPeersMessage(Peer peer, GetPeersMessage getPeersMessage) {
		log.trace("Received GetPeersMessage from {}", peer);
		try {
			// Deliver known Peers in its entirety, filtered on whitelist and activity
			// Chunk the sending of Peers so that UDP can handle it
			EUID requestingNid = peer.hasNID() ? peer.getNID() : null;
			List<PeerWithSystem> peers = addressbook.peers()
				.filter(Peer::hasSystem)
				.filter(p -> requestingNid == null || !requestingNid.equals(p.getNID())) // Exclude sender
				.filter(StandardFilters.standardFilter(localSystem.getNID(), whitelist))
				.filter(StandardFilters.recentlyActive(this.recencyThreshold))
				.collect(Collectors.toList());
			for (List<PeerWithSystem> batch : Lists.partition(peers, peerMessageBatchSize)) {
				PeersMessage peersMessage = new PeersMessage(this.universeMagic, ImmutableList.copyOf(batch));
				messageCentral.send(peer, peersMessage);
			}
		} catch (Exception ex) {
			log.error(String.format("peers.get %s", peer), ex);
		}
	}

	private void handlePeerPingMessage(Peer peer, PeerPingMessage message) {
		log.debug("Received PeerPingMessage from {}:{}", () -> peer, () -> formatNonce(message.getNonce()));
		try {
			long nonce = message.getNonce();
			long payload = message.getPayload();
			log.trace("peer.ping from {}:{}", () -> peer, () -> formatNonce(nonce));
			messageCentral.send(peer, new PeerPongMessage(this.universeMagic, nonce, payload, localSystem));
		} catch (Exception ex) {
			log.error(String.format("peer.ping %s", peer), ex);
		}
	}

	private void handlePeerPongMessage(Peer peer, PeerPongMessage message) {
		log.debug("Received PeerPongMessage from {}:{}", () -> peer, () -> formatNonce(message.getNonce()));
		try {
			synchronized (this.probes) {
				Long ourNonce = this.probes.get(peer);
				if (ourNonce != null) {
					long nonce = message.getNonce();
					long rtt = System.nanoTime() - message.getPayload();
					if (ourNonce.longValue() == nonce) {
						this.probes.remove(peer);
						log.debug("Got good peer.pong from {}:{}:{}ns", () -> peer, () -> formatNonce(nonce), () -> rtt);
					} else {
						if (nonce != 0L) {
							log.warn("Got mismatched peer.pong from {} with nonce '{}', ours '{}' ({}ns)",
								() -> peer, () -> formatNonce(nonce), () -> formatNonce(ourNonce), () -> rtt);
						}
					}
				}
			}
		} catch (Exception ex) {
			log.error(String.format("peer.pong %s", peer), ex);
		}
	}

	private void peersHousekeeping() {
		try {
			// Request peers information from connected nodes
			List<Peer> peers = addressbook.recentPeers().collect(Collectors.toList());
			if (!peers.isEmpty()) {
				int index = rand.nextInt(peers.size());
				Peer peer = peers.get(index);
				try {
					messageCentral.send(peer, new GetPeersMessage(this.universeMagic));
				} catch (TransportException ioex) {
					log.info(String.format("Failed to request peer information from %s",  peer), ioex);
				}
			}
		} catch (Exception t) {
			log.error("Peers update failed", t);
		}
	}

	private boolean probe(PeerWithSystem peer) {
		try {
			if (Time.currentTimestamp() - peer.getTimestamp(Timestamps.PROBED) < peerProbeFrequencyMs) {
				return false;
			}
			synchronized (this.probes) {
				if (!this.probes.containsKey(peer)) {
					long nonce = rng.nextLong();
					final PeerPingMessage ping = new PeerPingMessage(this.universeMagic, nonce, System.nanoTime(), localSystem);

					this.probes.put(peer, nonce);
					this.executor.schedule(() -> handleProbeTimeout(peer, nonce), peerProbeTimeoutMs, TimeUnit.MILLISECONDS);
					log.debug("Probing {}:{}", () -> peer, () -> formatNonce(nonce));
					messageCentral.send(peer, ping);
					peer.setTimestamp(Timestamps.PROBED, Time.currentTimestamp());
					return true;
				}
			}
		} catch (Exception ex) {
			log.error(String.format("Probe of peer %s failed", peer), ex);
		}
		return false;
	}

	private boolean nudge(TransportInfo transportInfo) {
		try {
			log.trace("Nudging {}", transportInfo);
			final PeerPingMessage ping = new PeerPingMessage(this.universeMagic, 0L, System.nanoTime(), localSystem);
			messageCentral.sendSystemMessage(transportInfo, ping);
			return true;
		} catch (Exception ex) {
			log.error(String.format("Nudge for %s failed", transportInfo), ex);
		}
		return false;
	}

	private void handleProbeTimeout(PeerWithSystem peer, long nonce) {
		synchronized (this.probes) {
			Long value = this.probes.get(peer);
			if (value != null && value.longValue() == nonce) {
				log.warn("Skipping peer removal {} for now...", () -> peer);
				//log.info("Removing peer {}:{} because of probe timeout", () -> peer, () -> formatNonce(nonce));
				//this.probes.remove(peer);
				//this.addressbook.removePeer(peer.getNID());
			}
		}
	}

	private void discoverPeers() {
		// Probe all the bootstrap hosts not in the address book so that they know about us
		bootstrapDiscovery.discoveryHosts().stream()
			.filter(ti -> this.addressbook.peer(ti).isEmpty())
			.forEachOrdered(this::nudge);
	}

	private String formatNonce(long nonce) {
		return Long.toHexString(nonce);
	}

	private boolean notOurNid(PeerWithSystem peer) {
		return !this.localSystem.getNID().equals(peer.getNID());
	}

	private boolean notInAddressBook(PeerWithSystem peer) {
		return this.addressbook.peer(peer.getNID()).isEmpty();
	}
}

