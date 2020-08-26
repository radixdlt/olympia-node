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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.containers.BasicContainer;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

// This could really be an interface, but some serialization quirks mean that
// interfaces can't currently be part of a serialization type hierarchy.
@SerializerId2("network.peer.base")
public abstract class Peer extends BasicContainer {
	protected static final Logger log = LogManager.getLogger();

	public static final int DEFAULT_BANTIME = 60 * 60;

	private static final long DEFAULT_TIMESTAMP = 0L;

	@JsonProperty("ban_reason")
	@DsonOutput(Output.PERSIST)
	private volatile String banReason;

	// Note that this map is fully populated on construction, and the
	// structure (keys and values) cannot be changed during the object's
	// lifetime.  Note that the contents of the values are changes, but
	// not the values themselves.
	private final Map<Timestamps, AtomicLong> timestamps;

	protected Peer() {
		banReason = null;
		EnumMap<Timestamps, AtomicLong> newTimestamps = new EnumMap<>(Timestamps.class);
		for (Timestamps t : Timestamps.values()) {
			newTimestamps.put(t, new AtomicLong(DEFAULT_TIMESTAMP));
		}
		this.timestamps = Collections.unmodifiableMap(newTimestamps);
	}

	protected Peer(Peer toCopy) {
		this.banReason = toCopy.banReason;
		EnumMap<Timestamps, AtomicLong> newTimestamps = new EnumMap<>(Timestamps.class);
		for (Timestamps t : Timestamps.values()) {
			newTimestamps.put(t, new AtomicLong(toCopy.getTimestamp(t)));
		}
		this.timestamps = Collections.unmodifiableMap(newTimestamps);
	}

	/**
	 * Returns the reason this peer is banned, as a human-readable text string.
	 * Note that the result is invalid if {@link #isBanned()} is {@code false}.
	 *
	 * @return The ban reason, or {@code null} if none specified
	 */
	public String getBanReason() {
		return banReason;
	}

	/**
	 * Marks the peer as banned for the specified reason.
	 * The peer will be banned for {@link #DEFAULT_BANTIME} seconds.
	 *
	 * @param reason the reason for the ban, as a human-readable string
	 */
	public void ban(String reason) {
		log.info("{} - Banned for {} seconds due to {}", this, DEFAULT_BANTIME, reason);
		this.banReason = reason;
		setTimestamp(Timestamps.BANNED, Time.currentTimestamp() + TimeUnit.SECONDS.toMillis(DEFAULT_BANTIME));
	}

	/**
	 * Marks the peer as banned for the specified reason, until the specified time.
	 *
	 * @param reason the reason for the ban, as a human-readable string
	 * @param until the time to ban the peer in epoch milliseconds
	 */
	public void setBan(String reason, long until) {
		this.banReason = reason;
		setTimestamp(Timestamps.BANNED, until);
	}

	/**
	 * Returns {@code true} if this peer is banned.
	 *
	 * @return {@code true} if this peer is banned, {@code false} otherwise
	 */
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


	/**
	 * Returns if this {@code Peer} has known system information.
	 *
	 * @return Return {@code true} if we know the system information of the peer, {@code false} otherwise
	 */
	public abstract boolean hasSystem();

	/**
	 * Returns the system information of the {@link Peer}.
	 *
	 * @return Return the system information of the {@link Peer}, or {@code null} if unknown
	 */
	public abstract RadixSystem getSystem();

	public long getTimestamp(Timestamps type) {
		return this.timestamps.get(type).get();
	}

	public void setTimestamp(Timestamps type, long timestamp) {
		this.timestamps.get(type).set(timestamp);
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
}
