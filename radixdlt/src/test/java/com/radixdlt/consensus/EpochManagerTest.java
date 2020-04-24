package com.radixdlt.consensus;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.mempool.Mempool;
import java.util.Collections;
import org.junit.Test;

public class EpochManagerTest {
	@Test
	public void when_next_epoch__then_should_create_new_event_coordinator() {
		EpochManager epochManager = new EpochManager(
			mock(ProposalGenerator.class),
			mock(Mempool.class),
			mock(EventCoordinatorNetworkSender.class),
			mock(SafetyRules.class),
			mock(Pacemaker.class),
			mock(VertexStore.class),
			mock(PendingVotes.class),
			proposers -> mock(ProposerElection.class),
			mock(ECKeyPair.class),
			mock(SystemCounters.class)
		);

		Validator validator = mock(Validator.class);
		when(validator.nodeKey()).thenReturn(mock(ECPublicKey.class));
		EventCoordinator eventCoordinator = epochManager.nextEpoch(ValidatorSet.from(Collections.singleton(validator)));
		assertNotNull(eventCoordinator);
	}
}