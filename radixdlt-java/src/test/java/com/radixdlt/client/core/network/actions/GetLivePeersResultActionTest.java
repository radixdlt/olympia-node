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