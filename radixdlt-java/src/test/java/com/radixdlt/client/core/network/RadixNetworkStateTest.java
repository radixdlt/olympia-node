/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

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
