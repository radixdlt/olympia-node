package com.radixdlt.client.core.network.actions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.network.RadixNode;
import org.junit.Test;

public class FetchAtomsObservationActionTest {
	@Test
	public void when_initializing_with_null__npe_should_be_thrown() {
		assertThatThrownBy(() -> FetchAtomsObservationAction.of(
			null,
			mock(RadixAddress.class),
			mock(RadixNode.class),
			mock(AtomObservation.class)
		)).isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> FetchAtomsObservationAction.of(
			"Hi",
			null,
			mock(RadixNode.class),
			mock(AtomObservation.class)
		)).isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> FetchAtomsObservationAction.of(
			"Hi",
			mock(RadixAddress.class),
			null,
			mock(AtomObservation.class)
		)).isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> FetchAtomsObservationAction.of(
			"Hi",
			mock(RadixAddress.class),
			mock(RadixNode.class),
			null
		)).isInstanceOf(NullPointerException.class);
	}
}