package com.radixdlt.test;

import java.util.ArrayList;

public class AssertionChecks {
	/**
	 * Gets the test builder for slow node BFT network tests.
	 *
	 * @return The test builder
	 */
	public static RemoteBFTTest.Builder slowNodeTestBuilder() {
		return RemoteBFTTest.builder()
			.assertAllProposalsHaveDirectParents()
			.assertNoTimeouts()
			.assertSafety()
			.assertLiveness();
	}

	/**
	 * Gets the test builder for latent BFT network tests.
	 *
	 * @return The test builder
	 */
	static RemoteBFTTest.Builder latentTestBuilder() {
		return RemoteBFTTest.builder()
			.assertResponsiveness()
			.assertAllProposalsHaveDirectParents()
			.assertNoTimeouts()
			.assertSafety()
			.assertLiveness(80);
	}

	/**
	 * Gets the test builder for latent BFT network tests.
	 *
	 * @return The test builder
	 * @param nodesToIgnore
	 */
	public static RemoteBFTTest.Builder outOfSynchronyTestBuilder(ArrayList<String> nodesToIgnore) {
		return RemoteBFTTest.builder()
			.assertResponsiveness(nodesToIgnore)
			.assertAllProposalsHaveDirectParents(nodesToIgnore)
			.assertSafety(nodesToIgnore)
			.assertLiveness(80,nodesToIgnore);
	}



}
