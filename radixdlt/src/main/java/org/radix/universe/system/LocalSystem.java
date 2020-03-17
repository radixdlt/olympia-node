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

package org.radix.universe.system;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.keys.Keys;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Bytes;
import org.radix.Radix;
import org.radix.common.executors.Executor;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.transport.DynamicTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.udp.PublicInetAddress;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.utils.SystemMetaData;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.radixdlt.serialization.MapHelper.mapOf;

@SerializerId2("api.local_system")
// FIXME reimplement localsystem as an interface, extract persistence to elsewhere
public final class LocalSystem extends RadixSystem
{
	private static final Logger log = Logging.getLogger();

	private ECKeyPair keyPair;

	@VisibleForTesting
	LocalSystem() throws CryptoException
	{
		super();

		this.keyPair = new ECKeyPair();
	}

	public LocalSystem(ECKeyPair key, String agent, int agentVersion, int protocolVersion, ImmutableList<TransportInfo> supportedTransports)
	{
		super(key.getPublicKey(), agent, agentVersion, protocolVersion, supportedTransports);
		this.keyPair = key;
	}

	public ECKeyPair getKeyPair() {
		return this.keyPair;
	}

	// Property "ledger" - 1 getter
	// No really obvious way of doing this better
	@JsonProperty("ledger")
	@DsonOutput(Output.API)
	Map<String, Object> getJsonLedger() {
		SystemMetaData smd = SystemMetaData.getInstance();

		Map<String, Object> latency = mapOf(
			"path", smd.get("ledger.latency.path", 0),
			"persist", smd.get("ledger.latency.persist", 0)
		);

		return mapOf(
			"processed", smd.get("ledger.processed", 0),
			"processing", smd.get("ledger.processing", 0),
			"stored", smd.get("ledger.stored", 0),
			"storedPerShard", smd.get("ledger.storedPerShard", "0"),
			"storing", smd.get("ledger.storing", 0),
			"storingPerShard", smd.get("ledger.storingPerShard", 0),
			"storing.peak", smd.get("ledger.storing.peak", 0),
			"checksum", smd.get("ledger.checksum", 0),
			"latency", latency
		);
	}

	// Property "global" - 1 getter
	@JsonProperty("global")
	@DsonOutput(Output.API)
	Map<String, Object> getJsonGlobal() {
		return mapOf(
			"stored", SystemMetaData.getInstance().get("ledger.network.stored", 0),
			"processing", SystemMetaData.getInstance().get("ledger.network.processing", 0),
			"storing", SystemMetaData.getInstance().get("ledger.network.storing", 0)
		);
	}

	// Property "events" - 1 getter
	@JsonProperty("events")
	@DsonOutput(Output.API)
	Map<String, Object> getJsonEvents() {
		SystemMetaData smd = SystemMetaData.getInstance();

		Map<String, Object> processed = mapOf(
			"synchronous", smd.get("events.processed.synchronous", 0L),
			"asynchronous", smd.get("events.processed.asynchronous", 0L)
		);

		return mapOf(
			"processed", processed,
			"processing", smd.get("events.processing", 0L),
			"broadcast",  smd.get("events.broadcast", 0L),
			"queued", smd.get("events.queued", 0L),
			"dequeued", smd.get("events.dequeued", 0L)
		);
	}

	// Property "messages" - 1 getter
	// No obvious improvements here
	@JsonProperty("messages")
	@DsonOutput(Output.API)
	Map<String, Object> getJsonMessages() {
		Map<String, Object> outbound = mapOf(
				"sent", SystemMetaData.getInstance().get("messages.outbound.sent", 0),
				"processed", SystemMetaData.getInstance().get("messages.outbound.processed", 0),
				"pending", SystemMetaData.getInstance().get("messages.outbound.pending", 0),
				"aborted", SystemMetaData.getInstance().get("messages.outbound.aborted", 0));
		Map<String, Object> inbound = mapOf(
				"processed", SystemMetaData.getInstance().get("messages.inbound.processed", 0),
				"received", SystemMetaData.getInstance().get("messages.inbound.received", 0),
				"pending", SystemMetaData.getInstance().get("messages.inbound.pending", 0),
				"discarded", SystemMetaData.getInstance().get("messages.inbound.discarded", 0));
		return mapOf(
				"inbound", inbound,
				"outbound", outbound);
	}

	// Property "processors" - 1 getter
	// No obvious improvements here
	@JsonProperty("processors")
	@DsonOutput(Output.API)
	int getJsonProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}

	public static LocalSystem restoreOrCreate(RuntimeProperties properties, Universe universe) {
		String nodeKeyPath = properties.get("node.key.path", "node.ks");
		ECKeyPair nodeKey = loadNodeKey(nodeKeyPath);
		LocalSystem localSystem;

		// if system has been persisted
		if (SystemMetaData.getInstanceOptional().map(meta -> meta.has("system") ).orElse(false)) {
			byte[] systemBytes = SystemMetaData.getInstance().get("system", Bytes.EMPTY_BYTES);
			try {
				localSystem = Serialization.getDefault().fromDson(systemBytes, LocalSystem.class);
			} catch (SerializationException e) {
				throw new IllegalStateException("while restoring local instance", e);
			}
		} else {
			localSystem = new LocalSystem(nodeKey, Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION, defaultTransports(universe));
		}

		// setup background checkpoint task to persist instance into SystemMetaData
		Executor.getInstance().scheduleAtFixedRate(new ScheduledExecutable(1, 1, TimeUnit.SECONDS) {
			@Override
			public void execute() {
				SystemMetaData.ifPresent(smc -> {
					try {
						byte[] systemBytes = Serialization.getDefault().toDson(localSystem, Output.PERSIST);
						smc.put("system", systemBytes);
					} catch (IOException e) {
						log.error("Could not persist system state", e);
					}
				});
			}
		});

		return localSystem;
	}

	// FIXME: *Really* need a better way of configuring this other than hardcoding here
	// Should also have the option of overriding "port", rather than always using universe port
	private static ImmutableList<TransportInfo> defaultTransports(Universe universe) {
		return ImmutableList.of(
			TransportInfo.of(
				UDPConstants.UDP_NAME,
				DynamicTransportMetadata.of(
					UDPConstants.METADATA_UDP_HOST, PublicInetAddress.getInstance()::toString,
					UDPConstants.METADATA_UDP_PORT, () -> Integer.toString(universe.getPort())
				)
			)
		);
	}

	private static ECKeyPair loadNodeKey(String nodeKeyPath) {
		try {
			return Keys.readKey(nodeKeyPath, "node", "RADIX_NODE_KEYSTORE_PASSWORD", "RADIX_NODE_KEY_PASSWORD");
		} catch (IOException | CryptoException ex) {
			throw new IllegalStateException("while loading node key", ex);
		}
	}
}