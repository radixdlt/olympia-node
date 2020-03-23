package com.radixdlt.consensus.liveness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.common.EUID;
import com.radixdlt.consensus.View;
import org.junit.Test;

public class DictatorshipTest {
	@Test
	public void when_retrieve_dictatorship_proposer__it_should_always_be_the_same() {
		Dictatorship dictatorship = new Dictatorship(EUID.ONE);
		assertThat(dictatorship.getProposer(mock(View.class))).isEqualTo(EUID.ONE);
		assertThat(dictatorship.isValidProposer(EUID.ONE, mock(View.class))).isTrue();
		assertThat(dictatorship.isValidProposer(mock(EUID.class), mock(View.class))).isFalse();
	}
}