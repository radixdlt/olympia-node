package com.radixdlt.client.core.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class RadixNetworkStateTest {
	@Test
	public void when_node_map_from_network_state_is_modified__then_exception_is_thrown() {
		RadixNetworkState state = new RadixNetworkState(new HashMap<>());
		Map<RadixNode, RadixNodeState> nodeMap = state.getNodeStates();
		assertThatThrownBy(() -> nodeMap.put(mock(RadixNode.class), mock(RadixNodeState.class)))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void when_initial_empty_node_map_for_network_state_is_modified__then_nodes_from_network_state_should_remain_empty() {
		Map<RadixNode, RadixNodeState> initialMap = new HashMap<>();
		RadixNetworkState state = new RadixNetworkState(initialMap);
		initialMap.put(mock(RadixNode.class), mock(RadixNodeState.class));
		assertThat(state.getNodeStates()).isEmpty();
	}
}
