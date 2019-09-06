package org.radix.network2.addressbook;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.radixdlt.common.EUID;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network.discovery.BootstrapDiscovery;
import org.radix.network.messages.GetPeersMessage;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.PeerPongMessage;
import org.radix.network.messages.PeersMessage;
import org.radix.network.peers.events.PeerAvailableEvent;
import org.radix.network2.messaging.MessageCentral;
import org.radix.network2.transport.TransportException;
import com.radixdlt.universe.Universe;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.SystemMessage;

// FIXME: static dependency on Modules.get(Universe.class).getPlanck()
// FIXME: static dependency on LocalSystem.getInstance().getNID()
public class PeerManager extends Plugin {
	private static final Logger log = Logging.getLogger("peermanager");

	private final Random rand = new Random(); // No need for cryptographically secure here
	private final Map<Peer, Long> probes = new HashMap<>();

	private final AddressBook addressbook;
	private final MessageCentral messageCentral;
	private final Events events;
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

	private Future heartbeatPeersFuture;
	private Future peersBroadcastFuture;
	private Future peerProbeFuture;
	private Future discoverPeersFuture;


	private class ProbeTask implements Runnable {
		private LinkedList<Peer> peersToProbe = new LinkedList<>();
		private int numPeers = 0;

		@Override
		public void run() {
			try {
				int numProbes = (int) (this.numPeers / TimeUnit.MILLISECONDS.toSeconds(Modules.get(Universe.class).getPlanck()));

				if (numProbes == 0) {
					numProbes = 16;
				}

				if (peersToProbe.isEmpty()) {
					addressbook.peers()
						.filter(StandardFilters.standardFilter())
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

	PeerManager(PeerManagerConfiguration config, AddressBook addressbook, MessageCentral messageCentral, Events events, BootstrapDiscovery bootstrapDiscovery) {
		super();

		this.addressbook = Objects.requireNonNull(addressbook);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.events = Objects.requireNonNull(events);
		this.bootstrapDiscovery = Objects.requireNonNull(bootstrapDiscovery);

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

		log.info(String.format("%s started, " +
						"peersBroadcastInterval=%s, peersBroadcastDelay=%s, peersProbeInterval=%s, " +
						"peersProbeDelay=%s, heartbeatPeersInterval=%s, heartbeatPeersDelay=%s, " +
						"discoverPeersInterval=%s, discoverPeersDelay=%s, peerProbeFrequency=%s",
			this.getClass().getSimpleName(),
				this.peersBroadcastIntervalMs, this.peersBroadcastDelayMs, this.peerProbeIntervalMs,
				this.peerProbeDelayMs, this.heartbeatPeersIntervalMs, this.heartbeatPeersDelayMs,
				this.discoverPeersIntervalMs, this.discoverPeersDelayMs, this.peerProbeFrequencyMs
		));
	}

	@Override
	public void start_impl() throws ModuleException {
		// Listen for messages
		register(PeersMessage.class, this::handlePeersMessage);
		register(GetPeersMessage.class, this::handleGetPeersMessage);
		register(PeerPingMessage.class, this::handlePeerPingMessage);
		register(PeerPongMessage.class, this::handlePeerPongMessage);
		register(SystemMessage.class, this::handleHeartbeatPeersMessage);

		// Tasks
		heartbeatPeersFuture = scheduleAtFixedRate(scheduledExecutable(heartbeatPeersDelayMs, heartbeatPeersIntervalMs, TimeUnit.MILLISECONDS, this::heartbeatPeers));
		peersBroadcastFuture = scheduleWithFixedDelay(scheduledExecutable(peersBroadcastDelayMs, peersBroadcastIntervalMs, TimeUnit.MILLISECONDS, this::peersHousekeeping));
		peerProbeFuture = scheduleWithFixedDelay(scheduledExecutable(peerProbeDelayMs, peerProbeIntervalMs, TimeUnit.MILLISECONDS, new ProbeTask()));
		discoverPeersFuture = scheduleWithFixedDelay(scheduledExecutable(discoverPeersDelayMs, discoverPeersIntervalMs, TimeUnit.MILLISECONDS, this::discoverPeers));
	}

	@Override
	public void stop_impl() throws ModuleException {
		unregister(PeersMessage.class);
		unregister(GetPeersMessage.class);
		unregister(PeerPingMessage.class);
		unregister(PeerPongMessage.class);
		unregister(SystemMessage.class);

		heartbeatPeersFuture.cancel(true);
		peersBroadcastFuture.cancel(true);
		peerProbeFuture.cancel(true);
		discoverPeersFuture.cancel(true);
	}

	@Override
	public String getName() {
		return "Peer Manager";
	}

	private void heartbeatPeers() {
		// System Heartbeat
		SystemMessage msg = new SystemMessage();
		addressbook.recentPeers().forEachOrdered(peer -> {
			try {
				messageCentral.send(peer, msg);
			} catch (TransportException ioex) {
				log.error("Could not send System heartbeat to " + peer, ioex);
			}
		});
	}

	private void handleHeartbeatPeersMessage(Peer peer, SystemMessage heartBeatMessage) {
		//TODO implement HeartBeat handler
	}

	private void handlePeersMessage(Peer peer, PeersMessage peersMessage) {
		List<Peer> peers = peersMessage.getPeers();
		if (peers != null) {
			EUID localNid = LocalSystem.getInstance().getNID();
			peers.stream()
				.filter(Peer::hasSystem)
				.filter(p -> !localNid.equals(p.getNID()))
				.forEachOrdered(addressbook::updatePeer);
		}
	}

	private void handleGetPeersMessage(Peer peer, GetPeersMessage getPeersMessage) {
		try {
			// Deliver known Peers in its entirety, filtered on whitelist and activity
			// Chunk the sending of Peers so that UDP can handle it
			PeersMessage peersMessage = new PeersMessage();
			List<Peer> peers = addressbook.peers()
				.filter(Peer::hasNID)
				.filter(StandardFilters.standardFilter())
				.filter(StandardFilters.recentlyActive())
				.collect(Collectors.toList());

			for (Peer p : peers) {
				if (p.getNID().equals(peer.getNID())) {
					// Know thyself
					continue;
				}

				peersMessage.getPeers().add(p);
				if (peersMessage.getPeers().size() == peerMessageBatchSize) {
					messageCentral.send(peer, peersMessage);
					peersMessage = new PeersMessage();
				}
			}

			if (!peersMessage.getPeers().isEmpty()){
				messageCentral.send(peer, peersMessage);
			}
		} catch (Exception ex) {
			log.error("peers.get " + peer, ex);
		}
	}

	private void handlePeerPingMessage(Peer peer, PeerPingMessage message) {
		try {
			long nonce = message.getNonce();
			log.debug("peer.ping from " + peer + " with nonce '" + nonce + "'");
			messageCentral.send(peer, new PeerPongMessage(nonce));
			events.broadcast(new PeerAvailableEvent(peer));
		} catch (Exception ex) {
			log.error("peer.ping " + peer, ex);
		}
	}

	private void handlePeerPongMessage(Peer peer, PeerPongMessage message) {
		try {
			synchronized (this.probes) {
				long nonce = message.getNonce();
				Long ourNonce = this.probes.get(peer);
				if (ourNonce != null && ourNonce.longValue() == nonce) {
					this.probes.remove(peer);
					log.debug("Got peer.pong from " + peer + " with nonce '" + nonce + "'");
					events.broadcast(new PeerAvailableEvent(peer));
				} else if (ourNonce != null) {
					log.debug("Got mismatched peer.pong from " + peer + " with nonce'" + nonce + "', ours '" + ourNonce + "'");
				}
			}
		} catch (Exception ex) {
			log.error("peer.pong " + peer, ex);
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
					messageCentral.send(peer, new GetPeersMessage());
				} catch (TransportException ioex) {
					log.info("Failed to request peer information from " + peer, ioex);
				}
			}
		} catch (Exception t) {
			log.error("Peers update failed", t);
		}
	}

	private boolean probe(Peer peer) {
		try {
			if(peer != null) {
				if (Time.currentTimestamp() - peer.getTimestamp(Timestamps.PROBED) < peerProbeFrequencyMs) {
					return false;
				}
				if (!this.probes.containsKey(peer)) {
					PeerPingMessage ping = new PeerPingMessage();

					// Only wait for response if peer has a system, otherwise peer will be upgraded by pong message
					long nonce = ping.getNonce();
					if (peer.hasSystem()) {
						this.probes.put(peer, nonce);
						schedule(scheduledExecutable(peerProbeTimeoutMs, 0, TimeUnit.MILLISECONDS, () -> handleProbeTimeout(peer, nonce)));
						log.debug("Probing "+peer+" with nonce '"+nonce+"'");
					} else {
						log.debug("Nudging "+peer);
					}
					messageCentral.send(peer, ping);
					peer.setTimestamp(Timestamps.PROBED, Time.currentTimestamp());
					return true;
				}
			}
		} catch (Exception ex) {
			log.error("Probe of peer " +peer + " failed", ex);
		}
		return false;
	}

	private void handleProbeTimeout(Peer peer, long nonce) {
		synchronized (this.probes) {
			Long value = this.probes.get(peer);
			if (value != null && value.longValue() == nonce) {
				log.info("Removing peer " + peer + " because of probe timeout");
				this.probes.remove(peer);
				this.addressbook.removePeer(peer);
			}
		}
	}

	private void discoverPeers() {
		// Probe all the bootstrap hosts so that they know about us
		GetPeersMessage msg = new GetPeersMessage();
		bootstrapDiscovery.discover(StandardFilters.standardFilter()).stream()
			.map(addressbook::peer)
			.forEachOrdered(peer -> {
				probe(peer);
				messageCentral.send(peer, msg);
			});
	}

	private ScheduledExecutable scheduledExecutable(long initialDelay, long recurrentDelay, TimeUnit units, Runnable r) {
		return new ScheduledExecutable(initialDelay, recurrentDelay, units) {
			@Override
			public void execute() {
				r.run();
			}
		};
	}
}

