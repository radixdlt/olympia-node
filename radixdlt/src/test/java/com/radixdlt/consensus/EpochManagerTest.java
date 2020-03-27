package com.radixdlt.consensus;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.mempool.Mempool;
import java.util.Collections;
import org.junit.Test;

public class EpochManagerTest {
	@Test
	public void when_next_epoch__then_should_create_new_event_coordinator() throws Exception {
		EpochManager epochManager = new EpochManager(
			mock(ProposalGenerator.class),
			mock(Mempool.class),
			mock(EventCoordinatorNetworkSender.class),
			mock(SafetyRules.class),
			mock(Pacemaker.class),
			mock(VertexStore.class),
			mock(PendingVotes.class),
			mock(ECKeyPair.class)
		);

		ECKeyPair ecKeyPair = ECKeyPair.generateNew();
		Validator validator = Validator.from(ecKeyPair.getPublicKey());
		EventCoordinator eventCoordinator = epochManager.nextEpoch(ValidatorSet.from(Collections.singleton(validator)));
		assertNotNull(eventCoordinator);
	}
}