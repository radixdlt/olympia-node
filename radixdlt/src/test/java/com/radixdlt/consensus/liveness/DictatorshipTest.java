package com.radixdlt.consensus.liveness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.View;
import com.radixdlt.crypto.ECPublicKey;
import org.junit.Test;

public class DictatorshipTest {
	@Test
	public void when_retrieve_dictatorship_proposer__it_should_always_be_the_same() {
		ECPublicKey ecPublicKey = mock(ECPublicKey.class);
		Dictatorship dictatorship = new Dictatorship(ecPublicKey);
		assertThat(dictatorship.getProposer(mock(View.class))).isEqualTo(ecPublicKey);
	}
}