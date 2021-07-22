package com.radixdlt.network.p2p.liveness;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.PeerEvent.PeerLostLiveness;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.properties.RuntimeProperties;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.stream.Stream;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class PeerLivenessMonitorTest {

	private P2PConfig p2PConfig;
	private PeersView peersView;
	private EventDispatcher<PeerEvent> peerEventDispatcher;
	private RemoteEventDispatcher<Ping> pingEventDispatcher;
	private RemoteEventDispatcher<Pong> pongEventDispatcher;
	private ScheduledEventDispatcher<PeerPingTimeout> pingTimeoutEventDispatcher;

	private PeerLivenessMonitor sut;

	@Before
	public void setup() throws ParseException {
		this.p2PConfig = P2PConfig.fromRuntimeProperties(new RuntimeProperties(new JSONObject(), new String[] {}));
		this.peersView = mock(PeersView.class);
		this.peerEventDispatcher = rmock(EventDispatcher.class);
		this.pingEventDispatcher = rmock(RemoteEventDispatcher.class);
		this.pongEventDispatcher = rmock(RemoteEventDispatcher.class);
		this.pingTimeoutEventDispatcher = rmock(ScheduledEventDispatcher.class);

		this.sut = new PeerLivenessMonitor(p2PConfig, peersView,
			peerEventDispatcher, pingEventDispatcher, pongEventDispatcher, pingTimeoutEventDispatcher);
	}

	@Test
	public void should_ping_peer_when_triggered_and_setup_a_timeout_and_emit_an_event_when_timeout() {
		final var peer1 = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
		when(peersView.peers()).thenReturn(Stream.of(PeersView.PeerInfo.fromBftNode(peer1)));

		this.sut.peersLivenessCheckTriggerEventProcessor().process(PeersLivenessCheckTrigger.create());

		verify(pingEventDispatcher, times(1)).dispatch(eq(peer1), any());
		verify(pingTimeoutEventDispatcher, times(1)).dispatch(any(), anyLong());

		this.sut.pingTimeoutEventProcessor().process(PeerPingTimeout.create(NodeId.fromPublicKey(peer1.getKey())));

		verify(peerEventDispatcher, times(1)).dispatch(argThat(arg ->
			arg instanceof PeerLostLiveness
				&& ((PeerLostLiveness) arg).getNodeId().equals(NodeId.fromPublicKey(peer1.getKey()))
		));
	}

	@Test
	public void should_ignore_obsolete_timeout() {
		final var peer1 = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
		when(peersView.peers()).thenReturn(Stream.of(PeersView.PeerInfo.fromBftNode(peer1)));

		this.sut.peersLivenessCheckTriggerEventProcessor().process(PeersLivenessCheckTrigger.create());

		verify(pingEventDispatcher, times(1)).dispatch(eq(peer1), any());
		verify(pingTimeoutEventDispatcher, times(1)).dispatch(any(), anyLong());

		this.sut.pongRemoteEventProcessor().process(peer1, Pong.create());
		this.sut.pingTimeoutEventProcessor().process(PeerPingTimeout.create(NodeId.fromPublicKey(peer1.getKey())));

		verifyNoInteractions(peerEventDispatcher);
	}

	@Test
	public void should_respond_with_pong_to_ping() {
		final var peer1 = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
		this.sut.pingRemoteEventProcessor().process(peer1, Ping.create());
		verify(pongEventDispatcher, times(1)).dispatch(eq(peer1), any());
	}
}
