package com.radixdlt.store;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class SpinStateMachineTransitionTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(SpinStateMachine.Transition.class).verify();
	}
}
