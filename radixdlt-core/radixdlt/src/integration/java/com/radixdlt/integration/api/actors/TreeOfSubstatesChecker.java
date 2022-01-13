package com.radixdlt.integration.api.actors;

import com.radixdlt.environment.deterministic.MultiNodeDeterministicRunner;
import com.radixdlt.integration.api.DeterministicActor;
import com.radixdlt.tree.substate.BerkeleySubStateStoreSpy;
import com.radixdlt.tree.substate.SubStateTree;
import com.radixdlt.utils.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

public final class TreeOfSubstatesChecker implements DeterministicActor
{
	private static final Logger logger = LogManager.getLogger();

	@Override
	public String execute(MultiNodeDeterministicRunner runner, Random random) throws Exception {

		byte[] temp = null;
		for (int i=0; i<runner.getSize(); i++ ) {
			var injector = runner.getNode(0);
			var berkeleySubStateStore = injector.getInstance(BerkeleySubStateStoreSpy.class);
			SubStateTree subStateTree = new SubStateTree(berkeleySubStateStore.getDatabase(), null);
			var rootHash = subStateTree.getHash();
			if (temp != null) {
				assertThat(Arrays.equals(rootHash, temp)).isTrue();
			}
			logger.debug("rootHash in: {} is: {}", i, Bytes.toHexString(rootHash));
			temp = rootHash;
		}
		return String.format("Okay{rootHash=%s}", Bytes.toHexString(temp));
	}
}
