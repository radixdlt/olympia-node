package com.radixdlt.test;

public class AssertionChecks {
	/**
	 * Gets the test builder for slow node BFT network tests.
	 *
	 * @return The test builder
	 */
	static RemoteBFTTest.Builder slowNodeTestBuilder() {
		return RemoteBFTTest.builder()
			.assertAllProposalsHaveDirectParents()
			.assertNoRejectedProposals()
			.assertNoSyncExceptions()
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
			.assertNoRejectedProposals()
			.assertNoSyncExceptions()
			.assertNoTimeouts()
			.assertSafety()
			.assertLiveness();
	}

}
