package com.radixdlt.integration.distributed;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;

import java.util.List;
import java.util.Set;

/**
 * Simple Mempool state computer
 */
public class MockedMempoolStateComputerModule extends AbstractModule {
	@Provides
	@Singleton
	private StateComputerLedger.StateComputer stateComputer(Mempool mempool, Hasher hasher) {
		return new StateComputerLedger.StateComputer() {
			@Override
			public void addToMempool(Command command) {
				try {
					mempool.add(command);
				} catch (MempoolFullException | MempoolDuplicateException e) {
				}
			}

			@Override
			public Command getNextCommandFromMempool(Set<HashCode> exclude) {
				final List<Command> commands = mempool.getCommands(1, exclude);
				return !commands.isEmpty() ? commands.get(0) : null;
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
			public void commit(VerifiedCommandsAndProof commands, VerifiedVertexStoreState vertexStoreState) {
				commands.getCommands().forEach(cmd -> mempool.removeCommitted(hasher.hash(cmd)));
			}
		};
	}
}
