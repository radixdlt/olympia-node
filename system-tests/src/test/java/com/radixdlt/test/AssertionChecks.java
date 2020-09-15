package com.radixdlt.test;

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
			.assertLiveness(20);
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
			.assertLiveness();
	}

}
