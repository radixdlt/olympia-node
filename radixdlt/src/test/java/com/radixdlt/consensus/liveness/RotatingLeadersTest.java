package com.radixdlt.consensus.liveness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.View;
import org.junit.Test;

public class RotatingLeadersTest {
	@Test
	public void when_getting_leader_for_view_greater_than_size__leaders_are_round_robined() {
		ImmutableList<EUID> leaders = ImmutableList.of(new EUID(0), new EUID(1));
		RotatingLeaders rotatingLeaders = new RotatingLeaders(leaders);
		assertThat(rotatingLeaders.getProposer(View.of(leaders.size())))
			.isEqualTo(leaders.get(0));
		assertThat(rotatingLeaders.isValidProposer(leaders.get(0), View.of(leaders.size())))
			.isTrue();
		assertThat(rotatingLeaders.isValidProposer(leaders.get(1), View.of(leaders.size())))
			.isFalse();
	}
}