package org.radix.network.peers;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.radix.collections.WireableSet;
import com.radixdlt.common.EUID;
import org.radix.containers.BasicContainer;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.Network;
import org.radix.network.Protocol;
import org.radix.network.messaging.Message;
import org.radix.network.peers.events.PeerBannedEvent;
import org.radix.network.peers.events.PeerConnectedEvent;
import org.radix.network.peers.events.PeerConnectingEvent;
import org.radix.network.peers.events.PeerDisconnectedEvent;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.udp.UDPConstants;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.state.State;
import org.radix.time.Chronologic;
import org.radix.time.NtpService;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

@SerializerId2("network.peer")
public class Peer extends BasicContainer implements Chronologic
{
	private static final Logger networkLog = Logging.getLogger ("network");

	private static final int DEFAULT_BANTIME = 60 * 60;

	@Override
	public short VERSION() { return 100;}

	private URI				host = null;
	private String 			banReason = null;
	private HashMap<String, Long> timestamps = new HashMap<>();

	private final Object LOCK = new Object();

	@JsonProperty("protocols")
	@DsonOutput(Output.ALL)
	private WireableSet<Protocol>	protocols = new WireableSet<>();

	@JsonProperty("system")
	@DsonOutput(Output.ALL)
	private RadixSystem system = new RadixSystem();
	private transient State	state = new State(State.NONE);

	public Peer()
	{
		super();
	}

	public Peer(URI host)
	{
		super();
		this.host = host;
	}

	protected Peer(URI host, Peer peer) {
		this(host);
		if (peer != null) {
			this.system = peer.system == null ? null : new RadixSystem(peer.system);
			this.host = peer.host; // URI is immutable
			this.timestamps = peer.timestamps == null ? null : new HashMap<>(peer.timestamps);
			this.banReason = peer.banReason;
			this.protocols = peer.protocols == null ? null : new WireableSet<>(peer.protocols);
		}
	}

	@Override
	public final boolean equals(Object object)
	{
		if (object == null) return false;
		if (object == this) return true;

		if (!object.getClass().equals(getClass()))
			return false;

		if (object instanceof Peer)
		{
			if (((Peer)object).getURI().getHost().equalsIgnoreCase(getURI().getHost())) // &&
//				((Peer)object).getURI().getPort() == getURI().getPort())
				return true;
		}

		return false;
	}

	@Override
	public final int hashCode()
	{
		int result = 17;

		result = 31 * result + host.getHost().toLowerCase().hashCode();
		result = 31 * result + getClass().hashCode();

		return result;
	}

	@Override
	public String toString()
	{
		return host.toString()+" ID:"+(this.system == null ? EUID.ZERO : this.system.getNID());
	}

	public boolean hasProtocol(Protocol protocol)
	{
		return protocols.contains(protocol);
	}

	public void addProtocol(Protocol protocol)
	{
		protocols.add(protocol);
	}

	public boolean isStream()
	{
		return false;
	}

	public URI getURI()
	{
		return host;
	}

	public String getBanReason()
	{
		return banReason;
	}

	public void setBanReason(String banReason)
	{
		this.banReason = banReason;
	}

	public RadixSystem getSystem()
	{
		return system;
	}

	public void setSystem(RadixSystem system)
	{
		this.system = system;
	}

	void onConnecting() {
		synchronized (LOCK) {
			setState(new State(State.CONNECTING));
			setTimestamp(Timestamps.ACTIVE, 0l);
			Events.getInstance().broadcast(new PeerConnectingEvent(this));
		}
	}

	void onConnected() {
		synchronized (LOCK) {
			setState(new State(State.CONNECTED));
			setTimestamp(Timestamps.CONNECTED, Time.currentTimestamp());
			Events.getInstance().broadcast(new PeerConnectedEvent(this));
		}
	}

	public void ban(String reason)
	{
		ban(reason, DEFAULT_BANTIME);
	}

	public void ban(String reason, int duration)
	{
		networkLog.info(toString()+" - Banned for "+duration+" seconds due to "+reason);

		setBanReason(reason);
		setTimestamp(Timestamps.BANNED, Modules.get(NtpService.class).getUTCTimeMS()+(duration*1000l));

		disconnect(reason);

		Events.getInstance().broadcast(new PeerBannedEvent(this));
	}

	public boolean isBanned()
	{
		return getTimestamp(Timestamps.BANNED) > java.lang.System.currentTimeMillis();
	}

	public synchronized void disconnect(String reason)
	{
		if (getState().in(State.DISCONNECTING) || getState().in(State.DISCONNECTED))
			return;

		try
		{
			setState(new State(State.DISCONNECTING));
			if (reason != null)
			{
				networkLog.error(toString()+" - Disconnected - "+reason);
			}
			else
			{
				networkLog.info(toString()+" - Disconnected");
			}
		}
		catch ( Exception e )
		{
			networkLog.error ("Exception in disconnect of "+toString(), e);
		}
		finally
		{
			onDisconnected();
		}
	}

	synchronized void onDisconnected()
	{
		setState(new State(State.DISCONNECTED));
		setTimestamp(Timestamps.DISCONNECTED, Modules.get(NtpService.class).getUTCTimeMS());
		Events.getInstance().broadcast(new PeerDisconnectedEvent(this));
	}

	public void send(Message message) throws IOException
	{
		throw new UnsupportedOperationException("Send not supported on Peer object");
	}

	// FIXME temporary until address book is sorted
	public TransportMetadata connectionData(String transport) {
		if (!UDPConstants.UDP_NAME.equals(transport) ) {
			throw new TransportException(String.format("Peer %s has no transport %s", getURI(), transport));
		}
		URI uri = getURI();
		return StaticTransportMetadata.of(
			UDPConstants.METADATA_UDP_HOST, uri.getHost(),
			UDPConstants.METADATA_UDP_PORT, String.valueOf(uri.getPort())
		);
	}

	// CHRONOLOGIC //
	@Override
	public long getTimestamp()
	{
		return timestamps.getOrDefault(Timestamps.DEFAULT, 0l);
	}

	@Override
	public long getTimestamp(String type)
	{
		return timestamps.getOrDefault(type, 0l);
	}

	@Override
	public void setTimestamp(String type, long timestamp)
	{
		timestamps.put(type, timestamp);
	}

	// STATE //
	public State getState()
	{
		return state;
	}

	private void setState(State state)
	{
		this.state.checkAllowed(state);
		this.state = state;
	}

	// Property "host" - 1 getter, 1 setter
	// Could potentially just serialize the URI as a string
	@JsonProperty("host")
	@DsonOutput(Output.ALL)
	private Map<String, Object> getJsonHost() {
		return ImmutableMap.of(
				"ip", this.host.getHost().toLowerCase(),
				"port", this.host.getPort());
	}

	@JsonProperty("host")
	private void setJsonHost(Map<String, Object> props) {
		String hostip = (String) props.get("ip");
		Integer port = ((Number) props.get("port")).intValue();

		this.host = Network.getURI(hostip, port);
	}

	// Property "timestamps" - 1 getter, 1 setter
	// FIXME: Should just serialize the underlying (serializable) Timestamps instance
	@JsonProperty("timestamps")
	@DsonOutput(Output.PERSIST)
	private Map<String, Long> getJsonTimestamps() {
		return ImmutableMap.<String, Long>builder()
				.put("connected", getTimestamp(Timestamps.CONNECTED))
				.put("disconnected", getTimestamp(Timestamps.DISCONNECTED))
				.put("probed", getTimestamp(Timestamps.PROBED))
				.put("active", getTimestamp(Timestamps.ACTIVE))
				.put("banned", getTimestamp(Timestamps.BANNED))
				.build();
	}

	@JsonProperty("timestamps")
	private void setJsonTimestamps(Map<String, Long> props) {
		setTimestamp(Timestamps.CONNECTED, props.get("connected").longValue());
		setTimestamp(Timestamps.DISCONNECTED, props.get("disconnected").longValue());
		setTimestamp(Timestamps.PROBED, props.get("probed").longValue());
		setTimestamp(Timestamps.ACTIVE, props.get("active").longValue());
		setTimestamp(Timestamps.BANNED, props.get("banned").longValue());
	}

	// Property "statistics" - 1 getter, 1 setter
	// Could potentially just add a new serializable POJO with these values included
	@JsonProperty("statistics")
	@DsonOutput(Output.PERSIST)
	private ImmutableMap<String, Long> getJsonStatistics() {
		return ImmutableMap.of("duration", getTimestamp(Timestamps.DISCONNECTED) - getTimestamp(Timestamps.CONNECTED));
	}

	// Property "ban_reason" - 1 getter, 1 setter
	// FIXME: Should just serialize if non-null
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
