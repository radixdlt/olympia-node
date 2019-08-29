package org.radix.network2.addressbook;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.radix.containers.BasicContainer;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.events.PeerBannedEvent;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.time.Chronologic;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

// This could really be an interface, but some serialization quirks mean that
// interfaces can't currently be part of a serialization type hierarchy.
@SerializerId2("network.peer.base")
public abstract class Peer extends BasicContainer implements Chronologic {
	protected static final Logger log = Logging.getLogger("addressbook");

	public static final int DEFAULT_BANTIME = 60 * 60;

	private String banReason;
	private HashMap<String, Long> timestamps;

	protected Peer() {
		banReason = null;
		timestamps = new HashMap<>();
	}

	protected Peer(Peer toCopy) {
		this.banReason = toCopy.banReason;
		this.timestamps = new HashMap<>(toCopy.timestamps);
	}

	public String getBanReason() {
		return banReason;
	}

	public void setBanReason(String banReason) {
		this.banReason = banReason;
	}

	public void ban(String reason) {
		ban(reason, DEFAULT_BANTIME);
	}

	public void ban(String reason, int duration) {
		log.info(toString()+" - Banned for "+duration+" seconds due to "+reason);
		setBanReason(reason);
		setTimestamp(Timestamps.BANNED, Time.currentTimestamp() + TimeUnit.SECONDS.toMillis(duration));
		Events.getInstance().broadcast(new PeerBannedEvent(this));
	}

	public boolean isBanned() {
		return getTimestamp(Timestamps.BANNED) > Time.currentTimestamp();
	}

	/**
	 * Returns the Node ID of the {@link Peer}.
	 *
	 * @return Return the Node ID of the {@link Peer}, or {@code EUID.ZERO} if unknown
	 */
	public abstract EUID getNID();

	/**
	 * Returns if this {@code Peer} has a known node ID.
	 *
	 * @return Return {@code true} if we know the node ID of the peer, {@code false} otherwise
	 */
	public abstract boolean hasNID();

	/**
	 * Returns the timestamps associated with the {@link Peer}.
	 *
	 * @return Return the timestamps associated with the {@link Peer}.
	 */
	public PeerTimestamps getTimestamps() {
		return PeerTimestamps.of(this.getTimestamp(Timestamps.ACTIVE), this.getTimestamp(Timestamps.BANNED));
	}

	/**
	 * Returns {@code true} or {@code false} indicating if this {@link Peer}
	 * supports the specified transport.
	 *
	 * @param transportName The transport to test for
	 * @return {@code true} if the {@link Peer} supports the transport, {@code false} otherwise
	 */
	public abstract boolean supportsTransport(String transportName);

	/**
	 * Returns a {@link Stream} of the transports supported by the {@link Peer}.
	 *
	 * @return a {@link Stream} of the transports supported by the {@link Peer}
	 */
	public abstract Stream<TransportInfo> supportedTransports();

	/**
	 * Return the connection data required to connect to this peer using the
	 * specified transport.
	 *
	 * @param transportName The transport for which the {@link TransportMetadata} is required
	 * @return The {@link TransportMetadata}
	 * @throws TransportException if the transport is not supported, or another error occurs
	 */
	public abstract TransportMetadata connectionData(String transportName);


	// Possibly legacy?  Not sure.
	public abstract boolean hasSystem();
	public abstract RadixSystem getSystem();

	// To be removed somehow
	public abstract void setSystem(RadixSystem system);

	// CHRONOLOGIC //
	@Override
	public long getTimestamp() {
		return timestamps.getOrDefault(Timestamps.DEFAULT, 0l);
	}

	@Override
	public long getTimestamp(String type) {
		return timestamps.getOrDefault(type, 0l);
	}

	@Override
	public void setTimestamp(String type, long timestamp) {
		timestamps.put(type, timestamp);
	}

	// Property "timestamps" - 1 getter, 1 setter
	@JsonProperty("timestamps")
	@DsonOutput(Output.PERSIST)
	private Map<String, Long> getJsonTimestamps() {
		return ImmutableMap.<String, Long>builder()
				.put("probed", getTimestamp(Timestamps.PROBED))
				.put("active", getTimestamp(Timestamps.ACTIVE))
				.put("banned", getTimestamp(Timestamps.BANNED))
				.build();
	}

	@JsonProperty("timestamps")
	private void setJsonTimestamps(Map<String, Long> props) {
		setTimestamp(Timestamps.PROBED, props.get("probed").longValue());
		setTimestamp(Timestamps.ACTIVE, props.get("active").longValue());
		setTimestamp(Timestamps.BANNED, props.get("banned").longValue());
	}

	// Property "ban_reason" - 1 getter, 1 setter
	@JsonProperty("ban_reason")
	@DsonOutput(Output.PERSIST)
	private String getJsonBanReason() {
		return (getTimestamp(Timestamps.BANNED) > 0) ? banReason : null;
	}

	@JsonProperty("ban_reason")
	private void setJsonBanReason(String banReason) {
		this.banReason = banReason;
	}
}
