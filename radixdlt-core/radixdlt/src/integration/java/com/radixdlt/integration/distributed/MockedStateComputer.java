package com.radixdlt.integration.distributed;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.VerifiedCommandsAndProof;

import java.util.Objects;
import java.util.Set;

public final class MockedStateComputer implements StateComputer {
	private final Hasher hasher;

	public MockedStateComputer(Hasher hasher) {
		this.hasher = Objects.requireNonNull(hasher);
	}

	@Override
	public void addToMempool(Command command) {
		// No-op
	}

	@Override
	public Command getNextCommandFromMempool(Set<HashCode> exclude) {
		return null;
	}

	@Override
	public StateComputerLedger.StateComputerResult prepare(
		ImmutableList<StateComputerLedger.PreparedCommand> previous,
		Command next,
		long epoch,
		View view,
		long timestamp
	) {
		return new StateComputerLedger.StateComputerResult(
			next == null
				? ImmutableList.of()
				: ImmutableList.of(new MockPrepared(next, hasher.hash(next))),
			ImmutableMap.of()
		);
	}

	@Override
	public void commit(VerifiedCommandsAndProof verifiedCommandsAndProof, VerifiedVertexStoreState vertexStoreState) {
		// No-op
	}
}
