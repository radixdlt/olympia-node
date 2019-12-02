package org.radix.universe.system;

import java.util.Map;
import java.util.stream.Stream;

import com.radixdlt.common.EUID;
import org.radix.containers.BasicContainer;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.CryptoException;
import org.radix.modules.Modules;
import org.radix.network2.transport.TransportInfo;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.serialization.SerializerId2;
import org.radix.shards.ShardRange;
import org.radix.shards.ShardSpace;
import org.radix.time.LogicalClock;
import com.radixdlt.universe.Universe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import static com.radixdlt.serialization.MapHelper.mapOf;

@SerializerId2("api.system")
public class RadixSystem extends BasicContainer
{
	@Override
	public short VERSION() { return 100; }

	private int  			agentVersion;
	private int  			protocolVersion;

	@JsonProperty("planck")
	@DsonOutput(Output.ALL)
	private int				planck;

	private String 			agent;

	@JsonProperty("shards")
	@DsonOutput(Output.ALL)
	private ShardSpace		shards;

	private LogicalClock	clock;

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private long			timestamp;

	@JsonProperty("transports")
	@DsonOutput(Output.ALL)
	private ImmutableList<TransportInfo> transports;

	private ECPublicKey		key;

	public RadixSystem()
	{
		super();

		this.agent = "unknown";
		this.agentVersion = 0;
		this.clock = new LogicalClock();
		this.planck = 0;
		this.protocolVersion = 0;
		this.shards = new ShardSpace(0l, new ShardRange(0, 0));
		this.timestamp = 0;
		this.transports = ImmutableList.of();
		this.key = null;
	}

	public RadixSystem(RadixSystem system)
	{
		super();

 		this.agent = system.getAgent();
 		this.agentVersion = system.getAgentVersion();
 		this.clock = new LogicalClock(system.getClock().get());
		this.planck = system.getPlanck();
		this.protocolVersion = system.getProtocolVersion();
		this.shards = new ShardSpace(system.getKey().getUID().getShard(), system.getShards().getRange());
		this.timestamp = system.getTimestamp();
		this.transports = system.transports;
		this.key = system.getKey();
	}

	public RadixSystem(ECPublicKey key, String agent, int agentVersion, int protocolVersion, ShardSpace shards, ImmutableList<TransportInfo> transports)
	{
		this();

		this.key = key;
		this.agent = agent;
		this.agentVersion = agentVersion;
		this.protocolVersion = protocolVersion;
		this.shards = shards;
		this.transports = transports;
	}

	public String getAgent()
	{
		return this.agent;
	}

	public int getAgentVersion()
	{
		return this.agentVersion;
	}

	public int getProtocolVersion()
	{
		return this.protocolVersion;
	}

	public int getPlanck()
	{
		return this.planck;
	}

	void setPlanck(int planck)
	{
		this.planck = planck;
	}

	public long getTimestamp()
	{
		return this.timestamp;
	}

	public void setTimestamp(long timestamp)
	{
		this.timestamp = timestamp;
	}

	public boolean isSynced(RadixSystem system)
	{
		return Math.abs(this.timestamp - system.timestamp) < Modules.get(Universe.class).getPlanck();
	}

	public boolean isAhead(RadixSystem system)
	{
		return this.timestamp > (system.timestamp + Modules.get(Universe.class).getPlanck());
	}

	/*	public boolean isSynced(System system)
	{
		if (system.getAtoms() == 0)
			return false;

		BigDecimal multiplier = BigDecimal.valueOf(system.getShards().getRange().getSpan()).divide(BigDecimal.valueOf(this.getShards().getRange().getSpan()), MathContext.DECIMAL64);
		long estimation = BigDecimal.valueOf(this.getAtoms()).multiply(multiplier, MathContext.DECIMAL64).longValue();
		long sqrtEstimation = (long) Math.sqrt(estimation);
		long sqrt2Estimation = (long) Math.sqrt(sqrtEstimation);
		long sqrtAtoms = (long) Math.sqrt(system.getAtoms());

		if (Math.abs(sqrtAtoms - sqrtEstimation) > sqrt2Estimation)
			return false;

		return true;
	}

	public boolean isAhead(System system)
	{
		if (system.getAtoms() == 0)
			return true;

		BigDecimal multiplier = BigDecimal.valueOf(this.getShards().getRange().getSpan()).divide(BigDecimal.valueOf(system.getShards().getRange().getSpan()), MathContext.DECIMAL64);
		long estimation = BigDecimal.valueOf(system.getAtoms()).multiply(multiplier, MathContext.DECIMAL64).longValue();
		long sqrtEstimation = (long) Math.sqrt(estimation);
		long sqrt2Estimation = (long) Math.sqrt(sqrtEstimation);
		long sqrtAtoms = (long) Math.sqrt(this.getAtoms());

		if (sqrtAtoms < sqrtEstimation - sqrt2Estimation)
			return false;

		return true;
	}*/

	public ShardSpace getShards()
	{
		return this.shards;
	}

	void setShards(ShardSpace shardSpace)
	{
		this.shards = shardSpace;
	}

	public LogicalClock getClock()
	{
		return clock;
	}

	void setClock(long clock)
	{
		this.clock.set(clock);
	}

	public Stream<TransportInfo> supportedTransports() {
		return transports.stream();
	}

	public ECPublicKey getKey()
	{
		return key;
	}

	void setKey(ECPublicKey key)
	{
		this.key = key;
	}

	public EUID getNID()
	{
		return this.key == null ? EUID.ZERO : this.key.getUID();
	}

	// Property "agent" - 1 getter, 1 setter
	// FIXME: Should be included in a serializable class
	@JsonProperty("agent")
	@DsonOutput(Output.ALL)
	Map<String, Object> getJsonAgent() {
		return mapOf(
				"name", this.agent,
				"version", this.agentVersion,
				"protocol", this.protocolVersion);
	}

	@JsonProperty("agent")
	void setJsonAgent(Map<String, Object> props) {
		this.agent = (String) props.get("name");
		this.agentVersion = ((Number) props.get("version")).intValue();
		this.protocolVersion = ((Number) props.get("protocol")).intValue();
	}

	// Property "clock" - 1 getter, 1 setter
	// Better may be to make LogicalClock serializable
	@JsonProperty("clock")
	@DsonOutput(Output.ALL)
	long getJsonClock() {
		return this.clock.get();
	}

	@JsonProperty("clock")
	void setJsonClock(long value) {
		this.clock = new LogicalClock(value);
	}

	// Property "key" - 1 getter, 1 setter
	// FIXME: Should serialize ECKeyPair directly
	@JsonProperty("key")
	@DsonOutput(Output.ALL)
	byte[] getJsonKey() {
		return (key == null) ? null : key.getBytes();
	}

	@JsonProperty("key")
	void setJsonKey(byte[] newKey) throws SerializationException {
		try {
			key = new ECPublicKey(newKey);
		} catch (CryptoException cex) {
			throw new SerializationException("Invalid key", cex);
		}
	}

	// Property "nid" - 1 getter
	@JsonProperty("nid")
	@DsonOutput(Output.ALL)
	EUID getJsonNid() {
		return this.key == null ? null : this.key.getUID();
	}
}
