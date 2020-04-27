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

import com.google.inject.Inject;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.transport.TransportException;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.common.executors.Executor;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.network.discovery.BootstrapDiscovery;
import org.radix.network.discovery.Whitelist;
import org.radix.network.messages.GetPeersMessage;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.PeerPongMessage;
import org.radix.network.messages.PeersMessage;
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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PeerManager {
	private static final Logger log = LogManager.getLogger("peermanager");

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

	private final SecureRandom rng;
	private final Whitelist whitelist;
	private final LocalSystem localSystem;
	private final Universe universe;

	private Future<?> heartbeatPeersFuture;
	private Future<?> peersBroadcastFuture;
	private Future<?> peerProbeFuture;
	private Future<?> discoverPeersFuture;

	private class ProbeTask implements Runnable {
		private LinkedList<Peer> peersToProbe = new LinkedList<>();
		private int numPeers = 0;

		@Override
		public void run() {
			try {
				int numProbes = (int) (this.numPeers / TimeUnit.MILLISECONDS.toSeconds(universe.getPlanck()));

				if (numProbes == 0) {
					numProbes = 16;
				}

				if (peersToProbe.isEmpty()) {
					addressbook.peers()
						.filter(StandardFilters.standardFilter(localSystem.getNID(), whitelist))
						.forEachOrdered(peersToProbe::add);
					this.numPeers = peersToProbe.size();
				}

				numProbes = Math.min(numProbes, peersToProbe.size());
				if (numProbes > 0) {
					List<Peer> toProbe = peersToProbe.subList(0, numProbes);
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
		Universe universe
	) {
		super();

		this.addressbook = Objects.requireNonNull(addressbook);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.bootstrapDiscovery = Objects.requireNonNull(bootstrapDiscovery);
		this.rng = Objects.requireNonNull(rng);
		this.localSystem = localSystem;
		this.universe = Objects.requireNonNull(universe);
		this.whitelist = Whitelist.from(properties);

		this.peersBroadcastIntervalMs = config.networkPeersBroadcastInterval(30000);
		this.peersBroadcastDelayMs = config.networkPeersBroadcastDelay(60000);

		this.peerProbeIntervalMs = config.networkPeersProbeInterval(1000);
		this.peerProbeDelayMs = config.networkPeersProbeDelay(0);
		this.peerProbeFrequencyMs = config.networkPeersProbeFrequency(30000);
		this.peerProbeTimeoutMs = config.networkPeersProbeTimeout(20000);

		this.heartbeatPeersIntervalMs = config.networkHeartbeatPeersInterval(10000);
		this.heartbeatPeersDelayMs = config.networkHeartbeatPeersDelay(10000);

		this.discoverPeersIntervalMs = config.networkDiscoverPeersInterval(60000);
		this.discoverPeersDelayMs = config.networkDiscoverPeersDelay(1000);

		this.peerMessageBatchSize = config.networkPeersMessageBatchSize(64);

		log.info("{} started, "
			+ "peersBroadcastInterval={}, peersBroadcastDelay={}, peersProbeInterval={}, "
			+ "peersProbeDelay={}, heartbeatPeersInterval={}, heartbeatPeersDelay={}, "
			+ "discoverPeersInterval={}, discoverPeersDelay={}, peerProbeFrequency={}",
			this.getClass().getSimpleName(),
			this.peersBroadcastIntervalMs, this.peersBroadcastDelayMs, this.peerProbeIntervalMs,
			this.peerProbeDelayMs, this.heartbeatPeersIntervalMs, this.heartbeatPeersDelayMs,
			this.discoverPeersIntervalMs, this.discoverPeersDelayMs, this.peerProbeFrequencyMs
		);
	}

	private Future<?> schedule(long initialDelayMillis, Runnable runnable) {
		ScheduledExecutable executable = new ScheduledExecutable(initialDelayMillis, 0, TimeUnit.MILLISECONDS) {
			@Override
			public void execute() {
				runnable.run();
			}
		};
		Executor.getInstance().schedule(executable);
		return executable.getFuture();
	}

	private Future<?> scheduleAtFixedRate(long initialDelayMillis, long recurrentDelayMillis, Runnable runnable) {
		ScheduledExecutable executable = new ScheduledExecutable(initialDelayMillis, recurrentDelayMillis, TimeUnit.MILLISECONDS) {
			@Override
			public void execute() {
				runnable.run();
			}
		};
		Executor.getInstance().scheduleAtFixedRate(executable);
		return executable.getFuture();
	}

	private Future<?> scheduleWithFixedDelay(long initialDelayMillis, long recurrentDelayMillis, Runnable runnable) {
		ScheduledExecutable executable = new ScheduledExecutable(initialDelayMillis, recurrentDelayMillis, TimeUnit.MILLISECONDS) {
			@Override
			public void execute() {
				runnable.run();
			}
		};
		Executor.getInstance().scheduleWithFixedDelay(executable);
		return executable.getFuture();
	}

	public void start() {
		// Listen for messages
		messageCentral.addListener(PeersMessage.class, this::handlePeersMessage);
		messageCentral.addListener(GetPeersMessage.class, this::handleGetPeersMessage);
		messageCentral.addListener(PeerPingMessage.class, this::handlePeerPingMessage);
		messageCentral.addListener(PeerPongMessage.class, this::handlePeerPongMessage);
		messageCentral.addListener(SystemMessage.class, this::handleHeartbeatPeersMessage);

		// Tasks
		heartbeatPeersFuture = scheduleAtFixedRate(heartbeatPeersDelayMs, heartbeatPeersIntervalMs, this::heartbeatPeers);
		peersBroadcastFuture = scheduleWithFixedDelay(peersBroadcastDelayMs, peersBroadcastIntervalMs, this::peersHousekeeping);
		peerProbeFuture = scheduleWithFixedDelay(peerProbeDelayMs, peerProbeIntervalMs, new ProbeTask());
		discoverPeersFuture = scheduleWithFixedDelay(discoverPeersDelayMs, discoverPeersIntervalMs, this::discoverPeers);

		log.info("PeerManager started");
	}

	public void stop() {
		messageCentral.removeListener(PeersMessage.class, this::handlePeersMessage);
		messageCentral.removeListener(GetPeersMessage.class, this::handleGetPeersMessage);
		messageCentral.removeListener(PeerPingMessage.class, this::handlePeerPingMessage);
		messageCentral.removeListener(PeerPongMessage.class, this::handlePeerPongMessage);
		messageCentral.removeListener(SystemMessage.class, this::handleHeartbeatPeersMessage);

		heartbeatPeersFuture.cancel(true);
		peersBroadcastFuture.cancel(true);
		peerProbeFuture.cancel(true);
		discoverPeersFuture.cancel(true);

		log.info("PeerManager stopped");
	}

	private void heartbeatPeers() {
		// System Heartbeat
		SystemMessage msg = new SystemMessage(localSystem, this.universe.getMagic());
		addressbook.recentPeers().forEachOrdered(peer -> {
			try {
				messageCentral.send(peer, msg);
			} catch (TransportException ioex) {
				log.error(String.format("Could not send System heartbeat to %s", peer), ioex);
			}
		});
	}

	private void handleHeartbeatPeersMessage(Peer peer, SystemMessage heartBeatMessage) {
		//TODO implement HeartBeat handler
	}

	private void handlePeersMessage(Peer peer, PeersMessage peersMessage) {
		log.trace("Received PeersMessage from {}", peer);
		List<Peer> peers = peersMessage.getPeers();
		if (peers != null) {
			EUID localNid = this.localSystem.getNID();
			peers.stream()
				.filter(Peer::hasSystem)
				.filter(p -> !localNid.equals(p.getNID()))
				.forEachOrdered(addressbook::updatePeer);
		}
	}

	private void handleGetPeersMessage(Peer peer, GetPeersMessage getPeersMessage) {
		log.trace("Received GetPeersMessage from {}", peer);
		try {
			// Deliver known Peers in its entirety, filtered on whitelist and activity
			// Chunk the sending of Peers so that UDP can handle it
			PeersMessage peersMessage = new PeersMessage(this.universe.getMagic());
			List<Peer> peers = addressbook.peers()
				.filter(Peer::hasNID)
				.filter(StandardFilters.standardFilter(localSystem.getNID(), whitelist))
				.filter(StandardFilters.recentlyActive(universe.getPlanck()))
				.collect(Collectors.toList());

			for (Peer p : peers) {
				if (p.getNID().equals(peer.getNID())) {
					// Know thyself
					continue;
				}

				peersMessage.getPeers().add(p);
				if (peersMessage.getPeers().size() == peerMessageBatchSize) {
					messageCentral.send(peer, peersMessage);
					peersMessage = new PeersMessage(this.universe.getMagic());
				}
			}

			if (!peersMessage.getPeers().isEmpty()) {
				messageCentral.send(peer, peersMessage);
			}
		} catch (Exception ex) {
			log.error(String.format("peers.get %s", peer), ex);
		}
	}

	private void handlePeerPingMessage(Peer peer, PeerPingMessage message) {
		log.trace("Received PeerPingMessage from {}:{}", () -> peer, () -> formatNonce(message.getNonce()));
		try {
			long nonce = message.getNonce();
			log.debug("peer.ping from {}:{}", () -> peer, () -> formatNonce(nonce));
			messageCentral.send(peer, new PeerPongMessage(nonce, localSystem, this.universe.getMagic()));
		} catch (Exception ex) {
			log.error(String.format("peer.ping %s", peer), ex);
		}
	}

	private void handlePeerPongMessage(Peer peer, PeerPongMessage message) {
		log.trace("Received PeerPongMessage from {}:{}", () -> peer, () -> formatNonce(message.getNonce()));
		try {
			synchronized (this.probes) {
				Long ourNonce = this.probes.get(peer);
				if (ourNonce != null) {
					long nonce = message.getNonce();
					if (ourNonce.longValue() == nonce) {
						this.probes.remove(peer);
						log.debug("Got good peer.pong from {}:{}", () -> peer, () -> formatNonce(nonce));
					} else {
						log.debug("Got mismatched peer.pong from {} with nonce '{}', ours '{}'",
							() -> peer, () -> formatNonce(nonce), () -> formatNonce(ourNonce));
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
					messageCentral.send(peer, new GetPeersMessage(this.universe.getMagic()));
				} catch (TransportException ioex) {
					log.info(String.format("Failed to request peer information from %s",  peer), ioex);
				}
			}
		} catch (Exception t) {
			log.error("Peers update failed", t);
		}
	}

	private boolean probe(Peer peer) {
		try {
			if (peer != null) {
				if (Time.currentTimestamp() - peer.getTimestamp(Timestamps.PROBED) < peerProbeFrequencyMs) {
					return false;
				}
				synchronized (this.probes) {
					if (!this.probes.containsKey(peer)) {
						PeerPingMessage ping = new PeerPingMessage(rng.nextLong(), localSystem, this.universe.getMagic());

						// Only wait for response if peer has a system, otherwise peer will be upgraded by pong message
						long nonce = ping.getNonce();
						if (peer.hasSystem()) {
							this.probes.put(peer, nonce);
							schedule(peerProbeTimeoutMs, () -> handleProbeTimeout(peer, nonce));
							log.debug("Probing {}:{}", () -> peer, () -> formatNonce(nonce));
						} else {
							log.debug("Nudging {}", peer);
						}
						messageCentral.send(peer, ping);
						peer.setTimestamp(Timestamps.PROBED, Time.currentTimestamp());
						return true;
					}
				}
			}
		} catch (Exception ex) {
			log.error(String.format("Probe of peer %s failed", peer), ex);
		}
		return false;
	}

	private void handleProbeTimeout(Peer peer, long nonce) {
		synchronized (this.probes) {
			Long value = this.probes.get(peer);
			if (value != null && value.longValue() == nonce) {
				log.info("Removing peer {}:{} because of probe timeout", () -> peer, () -> formatNonce(nonce));
				this.probes.remove(peer);
				this.addressbook.removePeer(peer);
			}
		}
	}

	private void discoverPeers() {
		// Probe all the bootstrap hosts so that they know about us
		GetPeersMessage msg = new GetPeersMessage(this.universe.getMagic());
		bootstrapDiscovery.discover(this.addressbook, StandardFilters.standardFilter(localSystem.getNID(), whitelist)).stream()
			.map(addressbook::peer)
			.forEachOrdered(peer -> {
				probe(peer);
				messageCentral.send(peer, msg);
			});
	}

	private String formatNonce(long nonce) {
		return Long.toHexString(nonce);
	}
}

