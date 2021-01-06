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

package com.radixdlt.client.core.network.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class GetLivePeersResultActionTest {
	@Test
	public void when_list_from_result_is_modified__then_exception_is_thrown() {
		GetLivePeersResultAction action = GetLivePeersResultAction.of(mock(RadixNode.class), new ArrayList<>());
		List<NodeRunnerData> result = action.getResult();
		assertThatThrownBy(() -> result.add(mock(NodeRunnerData.class)))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	public void when_initial_empty_list_for_action_is_modified__then_list_of_data_from_action_should_remain_empty() {
		List<NodeRunnerData> initialList = new ArrayList<>();
		GetLivePeersResultAction action = GetLivePeersResultAction.of(mock(RadixNode.class), initialList);
		initialList.add(mock(NodeRunnerData.class));
		assertThat(action.getResult()).isEmpty();
	}
}