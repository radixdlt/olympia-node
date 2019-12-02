package org.radix.universe.system;

import com.radixdlt.utils.Bytes;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import com.radixdlt.common.AID;
import org.radix.Radix;
import com.radixdlt.utils.Offset;
import org.radix.common.executors.Executor;
import org.radix.common.executors.ScheduledExecutable;
import com.radixdlt.utils.Pair;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.keys.Keys;
import com.radixdlt.crypto.CryptoException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network2.transport.DynamicTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.udp.PublicInetAddress;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializerId2;
import org.radix.shards.ShardSpace;
import com.radixdlt.universe.Universe;
import org.radix.utils.SystemMetaData;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import static com.radixdlt.serialization.MapHelper.mapOf;

@SerializerId2("api.local_system")
// FIXME reimplement localsystem as an interface, extract persistence to elsewhere
public final class LocalSystem extends RadixSystem
{
	private static final Logger log = Logging.getLogger ();

	private static LocalSystem instance = null;

	// FIXME: This is a pretty horrible way of ensuring unit tests are stable,
	// as freeMemory() can return varying numbers between calls.
	// This is adjusted via reflection in the unit tests to be something that
	// returns a constant.
	private static LongSupplier freeMemory = () -> Runtime.getRuntime().freeMemory();
	private static LongSupplier maxMemory = () -> Runtime.getRuntime().maxMemory();
	private static LongSupplier totalMemory = () -> Runtime.getRuntime().totalMemory();

	@VisibleForTesting
	public static synchronized void reset() {
		LocalSystem.instance = null;
	}

	public static synchronized LocalSystem getInstance()
	{
		if (LocalSystem.instance == null)
		{
			ECKeyPair nodeKey = null;

			try
			{
				String nodeKeyPath = Modules.get(RuntimeProperties.class).get("node.key.path", "node.ks");
				nodeKey = Keys.readKey(nodeKeyPath, "node", "RADIX_NODE_KEYSTORE_PASSWORD", "RADIX_NODE_KEY_PASSWORD");
			}
			catch (IOException | CryptoException ex)
			{
				throw new IllegalStateException(ex);
			}

			if (Modules.isAvailable(SystemMetaData.class) == true && Modules.get(SystemMetaData.class).has("system")) {
				try {
					byte[] systemBytes = Modules.get(SystemMetaData.class).get("system", Bytes.EMPTY_BYTES);
					instance = Modules.get(Serialization.class).fromDson(systemBytes, LocalSystem.class);

					if (LocalSystem.instance.getKeyPair().equals(nodeKey) == false) // TODO what happens if NODE_KEY has changed?  Dump loggables?  Dump DB?
						log.warn("Node key has changed from "+instance.getKeyPair().getUID()+" to "+nodeKey.getUID());

					LocalSystem.instance.setKeyPair(nodeKey);
				} catch (IOException ex) {
					log.error("Could not load persisted system state from SystemMetaData", ex);
				}
			}

			if (LocalSystem.instance == null) {
				LocalSystem.instance = new LocalSystem(
					nodeKey,
					Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION,
					Modules.get(RuntimeProperties.class).get("shards.range", ShardSpace.SHARD_CHUNK_RANGE)
				);
			}

			if (Modules.isAvailable(SystemMetaData.class) == true)
			{
				Executor.getInstance().scheduleAtFixedRate(new ScheduledExecutable(1, 1, TimeUnit.SECONDS)
				{
					@Override
					public void execute()
					{
						Modules.ifAvailable(SystemMetaData.class, smc -> {
							try {
								byte[] systemBytes = Modules.get(Serialization.class).toDson(getInstance(), Output.PERSIST);
								smc.put("system", systemBytes);
							} catch (IOException e) {
								log.error("Could not persist system state", e);
							}
						});
					}
				});
			}
		}

		return LocalSystem.instance;
	}

	private ECKeyPair keyPair;

	@VisibleForTesting
	LocalSystem() throws CryptoException
	{
		super();

		this.keyPair = new ECKeyPair();
	}

	public LocalSystem(ECKeyPair key, String agent, int agentVersion, int protocolVersion, long shards)
	{
		super(key.getPublicKey(), agent, agentVersion, protocolVersion, new ShardSpace(key.getUID().getShard(), shards), defaultTransports());
		this.keyPair = key;
	}

	@Override
	public void setShards(ShardSpace shards)
	{
		super.setShards(shards);
	}

	public ECKeyPair getKeyPair() {
		return this.keyPair;
	}

	private void setKeyPair(ECKeyPair keyPair) {
		this.keyPair = keyPair;
		super.setKey(keyPair.getPublicKey());
	}

	// Property "ledger" - 1 getter
	// No really obvious way of doing this better
	@JsonProperty("ledger")
	@DsonOutput(Output.API)
	Map<String, Object> getJsonLedger() {
		SystemMetaData smd = Modules.get(SystemMetaData.class);

		Map<String, Object> latency = mapOf(
			"path", smd.get("ledger.latency.path", 0),
			"persist", smd.get("ledger.latency.persist", 0)
		);

		Map<String, Object> faults = mapOf(
			"tears", smd.get("ledger.faults.tears", 0),
			"assists", smd.get("ledger.faults.assists", 0),
			"stitched", smd.get("ledger.faults.stitched", 0),
			"failed", smd.get("ledger.faults.failed", 0)
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
			"latency", latency,
			"faults", faults
		);
	}

	// Property "global" - 1 getter
	@JsonProperty("global")
	@DsonOutput(Output.API)
	Map<String, Object> getJsonGlobal() {
		return mapOf(
			"stored", Modules.get(SystemMetaData.class).get("ledger.network.stored", 0),
			"processing", Modules.get(SystemMetaData.class).get("ledger.network.processing", 0),
			"storing", Modules.get(SystemMetaData.class).get("ledger.network.storing", 0)
		);
	}

	// Property "events" - 1 getter
	@JsonProperty("events")
	@DsonOutput(Output.API)
	Map<String, Object> getJsonEvents() {
		SystemMetaData smd = Modules.get(SystemMetaData.class);

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
				"sent", Modules.get(SystemMetaData.class).get("messages.outbound.sent", 0),
				"processed", Modules.get(SystemMetaData.class).get("messages.outbound.processed", 0),
				"pending", Modules.get(SystemMetaData.class).get("messages.outbound.pending", 0),
				"aborted", Modules.get(SystemMetaData.class).get("messages.outbound.aborted", 0));
		Map<String, Object> inbound = mapOf(
				"processed", Modules.get(SystemMetaData.class).get("messages.inbound.processed", 0),
				"received", Modules.get(SystemMetaData.class).get("messages.inbound.received", 0),
				"pending", Modules.get(SystemMetaData.class).get("messages.inbound.pending", 0),
				"discarded", Modules.get(SystemMetaData.class).get("messages.inbound.discarded", 0));
		return mapOf(
				"inbound", inbound,
				"outbound", outbound);
	}

	// Property "memory" - 1 getter
	// No obvious improvements here
	@JsonProperty("memory")
	@DsonOutput(Output.API)
	Map<String, Object> getJsonMemory() {
		return mapOf(
				"free", freeMemory.getAsLong(),
				"total", totalMemory.getAsLong(),
				"max", maxMemory.getAsLong());
	}

	// Property "processors" - 1 getter
	// No obvious improvements here
	@JsonProperty("processors")
	@DsonOutput(Output.API)
	int getJsonProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}

	// FIXME: *Really* need a better way of configuring this other than hardcoding here
	// Should also have the option of overriding "port", rather than always using universe port
	private static ImmutableList<TransportInfo> defaultTransports() {
		return ImmutableList.of(
			TransportInfo.of(
				UDPConstants.UDP_NAME,
				DynamicTransportMetadata.of(
					UDPConstants.METADATA_UDP_HOST, PublicInetAddress.getInstance()::toString,
					UDPConstants.METADATA_UDP_PORT, () -> Integer.toString(Modules.get(Universe.class).getPort())
				)
			)
		);
	}
}