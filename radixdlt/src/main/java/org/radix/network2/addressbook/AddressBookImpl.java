package org.radix.network2.addressbook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;

import org.radix.database.DatabaseEnvironment;
import org.radix.common.executors.Executor;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.database.DatabaseStore;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleResetException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.network.Network;
import org.radix.network.discovery.BootstrapDiscovery;
import org.radix.network.messages.GetPeersMessage;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.PeerPongMessage;
import org.radix.network.messages.PeersMessage;
import org.radix.network.peers.PeerTask;
import org.radix.network.peers.events.PeerAvailableEvent;
import org.radix.network.peers.filters.PeerBroadcastFilter;
import org.radix.network.peers.filters.PeerFilter;
import org.radix.network2.messaging.MessageCentral;
import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.utils.Locking;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.universe.Universe;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;

import org.radix.time.NtpService;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.SystemMessage;

import com.radixdlt.utils.RadixConstants;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

public class AddressBookImpl extends DatabaseStore implements AddressBook {
	private static final Logger log = Logging.getLogger("addressbook");

	private static final int MAX_CONNECTION_ATTEMPTS = 10;
	private static final long PROBE_TIMEOUT_SECONDS = 20;

	private final Random rand = new Random(); // No need for cryptographically secure here
	private final Serialization serialization;

	private final Lock peersLock = new ReentrantLock();
	private final Map<EUID, Peer>          peersByNid  = new HashMap<>();
	private final Map<TransportInfo, Peer> peersByInfo = new HashMap<>();

	private final Map<Peer, Long> probes = new HashMap<>(); // TODO can convert to simpler Map<EUID, Long>?

	private Database 			peersByNidDB;
	private SecondaryDatabase 	peersByInfoDB;

	private final long inactivityLimitMs;
	private final int peersBroadcastIntervalSec;
	private final int peersProbeIntervalSec;
	private final long peerProbeDelayMs;


	private class ProbeTask implements Runnable {
		private LinkedList<Peer> peersToProbe = new LinkedList<>();
		private int index = 0;
		private int numPeers = 0;

		@Override
		public void run() {
			try {
				int numProbes = (int) (this.numPeers / TimeUnit.MILLISECONDS.toSeconds(Modules.get(Universe.class).getPlanck()));

				if (numProbes == 0) {
					numProbes = 16;
				}

				if (peersToProbe.size() < numProbes) {
					PeerFilter filter = new PeerFilter();
					peers()
						.filter(p -> !filter.filter(p))
						.forEachOrdered(peersToProbe::add);
					this.numPeers = Math.max(this.numPeers, peersToProbe.size());
				}

				numProbes = Math.min(numProbes, peersToProbe.size());
				if (numProbes > 0) {
					List<Peer> toProbe = peersToProbe.subList(0, numProbes);
					toProbe.forEach(AddressBookImpl.this::probe);
					toProbe.clear();
				}
			} catch (Exception ex) {
				log.error("Peer probing failed", ex);
			}
		}
	}

	private class ProbeTimeout extends PeerTask {
		private final long nonce;

		ProbeTimeout(final Peer peer, final long nonce, long delay, TimeUnit unit) {
			super(peer, delay, unit);
			this.nonce = nonce;
		}

		@Override
		public void execute() {
			synchronized(AddressBookImpl.this.probes) {
				if (AddressBookImpl.this.probes.containsKey(getPeer()) && AddressBookImpl.this.probes.get(getPeer()).longValue() == this.nonce) {
					AddressBookImpl.this.probes.remove(getPeer());
					removePeer(getPeer());
				}
			}
		}
	}

	public AddressBookImpl(Serialization serialization) {
		super();

		this.serialization = Objects.requireNonNull(serialization);
		this.inactivityLimitMs = TimeUnit.SECONDS.toMillis(Modules.get(RuntimeProperties.class).get("network.peer.inactivity", 60));
		this.peersBroadcastIntervalSec = Modules.get(RuntimeProperties.class).get("network.peers.broadcast.interval", 30);
		this.peersProbeIntervalSec = Modules.get(RuntimeProperties.class).get("network.peers.probe.interval", 1);
		this.peerProbeDelayMs = TimeUnit.SECONDS.toMillis(Modules.get(RuntimeProperties.class).get("network.peer.probe.delay", 30));
	}

	@Override
	public void start_impl() throws ModuleException {
		DatabaseConfig config = new DatabaseConfig();
		config.setAllowCreate(true);

		try {
			this.peersByNidDB = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "peers_by_nid", config);

			SecondaryConfig infoConfig = new SecondaryConfig();
			infoConfig.setAllowCreate(true);
			infoConfig.setMultiKeyCreator(this::createTransportInfoKeys);
			infoConfig.setSortedDuplicates(true);
			this.peersByInfoDB = Modules.get(DatabaseEnvironment.class).getEnvironment().openSecondaryDatabase(null, "peers_by_info", this.peersByNidDB, infoConfig);
		} catch (DatabaseException | IllegalArgumentException | IllegalStateException ex) {
        	throw new ModuleStartException(ex, this);
		}

		super.start_impl();

		try {
			Locking.withLock(this.peersLock, this::loadDatabase);
			Locking.withLock(this.peersLock, this::removeNotWhitelisted);
		} catch (Exception ex) {
			throw new ModuleStartException(ex, this);
		}

		// Listen for messages
		register(PeersMessage.class, this::handlePeersMessage);
		register(GetPeersMessage.class, this::handleGetPeersMessage);
		register(PeerPingMessage.class, this::handlePeerPingMessage);
		register(PeerPongMessage.class, this::handlePeerPongMessage);

		// Tasks
		scheduleAtFixedRate(scheduledExecutable(10L, 10L, TimeUnit.SECONDS, this::heartbeatPeers));
		scheduleWithFixedDelay(scheduledExecutable(60, peersBroadcastIntervalSec, TimeUnit.SECONDS, this::peersHousekeeping));
		scheduleWithFixedDelay(scheduledExecutable(0,  peersProbeIntervalSec, TimeUnit.SECONDS, new ProbeTask()));
		scheduleWithFixedDelay(scheduledExecutable(1, 60, TimeUnit.SECONDS, this::discoverPeers));

	}

	@Override
	public void reset_impl() throws ModuleException {
		Transaction transaction = null;

		try {
			transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "peers", false);
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "peer_nids", false);
			transaction.commit();
		} catch (DatabaseNotFoundException dsnfex) {
			if (transaction != null) {
				transaction.abort();
			}
			log.warn(dsnfex.getMessage());
		} catch (Exception ex) {
			if (transaction != null) {
				transaction.abort();
			}
			throw new ModuleResetException(ex, this);
		}
	}

	@Override
	public void stop_impl() throws ModuleException {
		super.stop_impl();

		this.peersByInfoDB.close();
		this.peersByNidDB.close();
	}

	@Override
	public void build() throws DatabaseException {
		// Not used
	}

	@Override
	public void maintenence() throws DatabaseException {
		// Not used
	}

	@Override
	public void integrity() throws DatabaseException {
		// Not used
	}

	@Override
	public void flush() throws DatabaseException  {
		// Not used
	}

	@Override
	public String getName() {
		return "Peer Address Book";
	}

	@Override
	public boolean addPeer(Peer peer) {
		return handleUpdatedPeers(Locking.withFunctionLock(this.peersLock, this::addUpdatePeerInternal, peer));
	}

	@Override
	public boolean removePeer(Peer peer) {
		return handleUpdatedPeers(Locking.withFunctionLock(this.peersLock, this::removePeerInternal, peer));
	}

	@Override
	public boolean updatePeer(Peer peer) {
		return handleUpdatedPeers(Locking.withFunctionLock(this.peersLock, this::addUpdatePeerInternal, peer));
	}

	@Override
	public Peer peer(EUID nid) {
		Peer p = Locking.withFunctionLock(this.peersLock, this.peersByNid::get, nid);
		if (p == null) {
			p = new NidOnlyPeer(nid);
			addPeer(p);
		}
		return p;
	}

	@Override
	public Peer peer(TransportInfo transportInfo) {
		Peer p = Locking.withFunctionLock(this.peersLock, this.peersByInfo::get, transportInfo);
		if (p == null) {
			p = new UriOnlyPeer(transportInfo);
			addPeer(p);
		}
		return p;
	}

	@Override
	public Stream<Peer> peers() {
		// FIXME: Think about how to do this in a not so copying way
		return Locking.withSupplierLock(this.peersLock, () -> ImmutableSet.copyOf(this.peersByInfo.values())).stream();
	}

	@Override
	public Stream<Peer> recentPeers() {
		return peers().filter(this::isRecentPeer);
	}

	private boolean handleUpdatedPeers(Pair<Peer, Peer> updatedPeers) {
		if (updatedPeers != null) {
			if (updatedPeers.getFirst() != null) {
				Events.getInstance().broadcast(new PeersAddedEvent(ImmutableList.of(updatedPeers.getFirst())));
			}
			if (updatedPeers.getSecond() != null) {
				Events.getInstance().broadcast(new PeersRemovedEvent(ImmutableList.of(updatedPeers.getSecond())));
			}
			return updatedPeers.getFirst() != null || updatedPeers.getSecond() != null;
		}
		return false;
	}

	// Needs peersLock held
	// FIXME: revisit logic here - especially around NID changes
	private Pair<Peer, Peer> addUpdatePeerInternal(Peer peer) {
		// Handle specially if it's a connection-only peer (no NID)
		if (!peer.hasNID()) {
			// We don't save connection-only peers to the database
			boolean changed = peer.supportedTransports()
				.map(t -> peersByInfo.putIfAbsent(t, peer) == null)
				.reduce(false, (a, b) -> a | b);
			return changed ? Pair.of(peer, null) : null;
		}

		Peer oldPeer = peersByNid.get(peer.getNID());
		if (oldPeer == null || peer.hasSystem()) {
			// Add new peer
			updatePeerInternal(peer);
			return Pair.of(peer, null);
		}
		// No change
		return null;
	}

	// Needs peersLock held
	// Note that peer must have a nid to get here
	private void updatePeerInternal(Peer peer) {
		EUID nid = peer.getNID();
		try {
			DatabaseEntry key = new DatabaseEntry(nid.toByteArray());
			byte[] bytes = Modules.get(Serialization.class).toDson(peer, Output.PERSIST);
			DatabaseEntry value = new DatabaseEntry(bytes);
			if (peersByNidDB.put(null, key, value) == OperationStatus.SUCCESS) {
				peersByNid.put(nid, peer);
				peer.supportedTransports()
					.forEachOrdered(t -> peersByInfo.put(t, peer));
			}
		} catch (SerializationException e) {
			log.error("Failure updating " + peer + " associated with " + nid);
		}
	}

	// Needs peersLock held
	private Pair<Peer, Peer> removePeerInternal(Peer peer) {
		if (!peer.hasNID()) {
			// We didn't save connection-only peers to the database
			boolean changed = peer.supportedTransports()
				.map(t -> peersByInfo.remove(t) != null)
				.reduce(false, (a, b) -> a | b);
			return changed ? Pair.of(null, peer) : null;
		} else {
			EUID nid = peer.getNID();
			Peer oldPeer = peersByNid.remove(nid);
			if (oldPeer != null) {
				peer.supportedTransports().forEachOrdered(peersByInfo::remove);
				DatabaseEntry key = new DatabaseEntry(nid.toByteArray());
				if (peersByNidDB.delete(null, key) == OperationStatus.SUCCESS) {
					log.info("Removed " + oldPeer + " associated with " + nid);
				} else {
					log.error("Failure removing " + oldPeer + " associated with " + nid);
				}
			}
			return Pair.of(null, oldPeer);
		}
	}


	private boolean isRecentPeer(Peer p) {
		// Don't include banned peers
		PeerTimestamps pt = p.getTimestamps();
		if (pt.getBanned() > Time.currentTimestamp()) {
			// Banned!
			return false;
		}
		return p.getTimestamps().getActive() + inactivityLimitMs > Time.currentTimestamp();
	}

	// Needs peerLock held
	private void loadDatabase() {
		this.peersByInfo.clear();
		this.peersByNid.clear();
		try (Cursor cursor = this.peersByNidDB.openCursor(null, null)) {
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();

			while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				Peer peer = this.serialization.fromDson(value.getData(), Peer.class);
				peer.setTimestamp(Timestamps.CONNECTED, 0);
				byte[] bytes = this.serialization.toDson(peer, Output.PERSIST);
				this.peersByNidDB.put(null, key, new DatabaseEntry(bytes));
				EUID nid = peer.getNID();
				this.peersByNid.put(nid, peer);
				peer.supportedTransports()
					.forEachOrdered(ti -> this.peersByInfo.put(ti, peer));
			}
		} catch (IOException ex) {
			throw new UncheckedIOException("Error while loading database", ex);
		}
	}

	// Needs peerLock held
	private void removeNotWhitelisted() {
		// Clean out any existing non-whitelisted peers from the store (whitelist may have changed since last execution)
		// Take copy to avoid CoMoException
		ImmutableList<Peer> allPeers = ImmutableList.copyOf(peersByNid.values());
		for (Peer peer : allPeers) {
			// FIXME: Maybe consider making this per transport at some point
			if (peer.supportedTransports().anyMatch(this::hostNotWhitelisted)) {
				log.info("Deleting " + peer + ", as not whitelisted");
				removePeer(peer);
			}
		}
	}

	private boolean hostNotWhitelisted(TransportInfo ti) {
		String host = ti.metadata().get("host");
		if (host != null) {
			if (!Network.getInstance().isWhitelisted(host)) {
				return true;
			}
		}
		return false;
	}

	private void heartbeatPeers() {
		// System Heartbeat
		SystemMessage msg = new SystemMessage();
		recentPeers().forEachOrdered(peer -> {
			try {
				Modules.get(MessageCentral.class).send(peer, msg);
			} catch (TransportException ioex) {
				log.error("Could not send System heartbeat to " + peer, ioex);
			}
		});
	}

	private void createTransportInfoKeys(SecondaryDatabase database, DatabaseEntry key, DatabaseEntry value, Set<DatabaseEntry> secondaries) {
		try {
			Peer peer = serialization.fromDson(value.getData(), Peer.class);
			peer.supportedTransports()
				.map(TransportInfo::toString)
				.map(s -> s.getBytes(RadixConstants.STANDARD_CHARSET))
				.map(DatabaseEntry::new)
				.forEachOrdered(secondaries::add);
		} catch (SerializationException ex) {
			log.error("TransportInfo keys failed for Peer", ex);
		}
	}

	private ScheduledExecutable scheduledExecutable(long initialDelay, long recurrentDelay, TimeUnit units, Runnable r) {
		return new ScheduledExecutable(initialDelay, recurrentDelay, units) {
			@Override
			public void execute() {
				r.run();
			}
		};
	}

	private void handlePeersMessage(Peer peer, PeersMessage peersMessage) {
		List<Peer> peers = peersMessage.getPeers();
		if (peers != null) {
			EUID localNid = LocalSystem.getInstance().getNID();
			peers.stream()
			.filter(p -> p.getSystem() != null)
			.filter(p -> !localNid.equals(p.getSystem().getNID()))
			.forEachOrdered(this::updatePeer);
		}
	}

	private void handleGetPeersMessage(Peer peer, GetPeersMessage getPeersMessage) {
		try {
			// Deliver known Peers in its entirety, filtered on whitelist and activity
			// Chunk the sending of Peers so that UDP can handle it
			PeersMessage peersMessage = new PeersMessage();
			PeerBroadcastFilter filter = new PeerBroadcastFilter();
			List<Peer> peers = peers()
				.filter(Peer::hasNID)
				.filter(p -> !filter.filter(p))
				.collect(Collectors.toList());

			for (Peer p : peers) {
				if (p.getNID().equals(peer.getNID())) {
					continue;
				}

				peersMessage.getPeers().add(p);

				if (peersMessage.getPeers().size() == 64) {
					Modules.get(MessageCentral.class).send(peer, peersMessage);
					peersMessage = new PeersMessage();
				}
			}

			if (!peersMessage.getPeers().isEmpty()){
				Modules.get(MessageCentral.class).send(peer, peersMessage);
			}
		} catch (Exception ex) {
			log.error("peers.get " + peer, ex);
		}
	}

	private void handlePeerPingMessage(Peer peer, PeerPingMessage message) {
		try {
			long nonce = message.getNonce();
			log.debug("peer.ping from " + peer + " with nonce '" + nonce + "'");
			Modules.get(MessageCentral.class).send(peer, new PeerPongMessage(nonce));
			Events.getInstance().broadcast(new PeerAvailableEvent(peer));
		} catch (Exception ex) {
			log.error("peer.ping " + peer, ex);
		}
	}

	private void handlePeerPongMessage(Peer peer, PeerPongMessage message) {
		try {
			synchronized (this.probes) {
				long nonce = message.getNonce();
				if (this.probes.containsKey(peer) && this.probes.get(peer).longValue() == nonce) {
					this.probes.remove(peer);
					log.debug("Got peer.pong from " + peer + " with nonce '" + nonce + "'");
					Events.getInstance().broadcast(new PeerAvailableEvent(peer));
				} else {
					log.debug("Got peer.pong without matching probe from " + peer + " with nonce '" + nonce + "'");
				}
			}
		} catch (Exception ex) {
			log.error("peer.pong " + peer, ex);
		}
	}

	private void peersHousekeeping() {
		try {
			// Request peers information from connected nodes
			List<Peer> peers = Modules.get(AddressBook.class).recentPeers().collect(Collectors.toList());
			if (!peers.isEmpty()) {
				int index = rand.nextInt(peers.size());
				Peer peer = peers.get(index);
				try {
					Modules.get(MessageCentral.class).send(peer, new GetPeersMessage());
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
			synchronized(this.probes) {
				if (peer != null && (Time.currentTimestamp() - peer.getTimestamp(Timestamps.PROBED) < peerProbeDelayMs)) {
					return false;
				}

				if (peer != null && !this.probes.containsKey(peer)) {
					PeerPingMessage ping = new PeerPingMessage();

					this.probes.put(peer, ping.getNonce());
					log.debug("Probing "+peer+" with nonce '"+ping.getNonce()+"'");
					Executor.getInstance().schedule(new ProbeTimeout(peer, ping.getNonce(), PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS));

					Modules.get(MessageCentral.class).send(peer, ping);
					peer.setTimestamp(Timestamps.PROBED, Modules.get(NtpService.class).getUTCTimeMS());
					return true;
				}
			}
		} catch (Exception ex) {
			log.error("Probe of peer " +peer + " failed", ex);
		}

		return false;
	}

	private void discoverPeers() {
		// Probe all the bootstrap hosts so that they know about us //
		Collection<Peer> bootstrap = BootstrapDiscovery.getInstance().discover(new PeerFilter()).stream()
			.map(Modules.get(AddressBook.class)::peer)
			.collect(ImmutableList.toImmutableList());

		for (Peer peer : bootstrap) {
			probe(peer);
		}

		// Ask them for known peers too //
		GetPeersMessage msg = new GetPeersMessage();
		for (Peer peer : bootstrap) {
			Modules.get(MessageCentral.class).send(peer, msg);
		}
	}
}

