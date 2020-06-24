package com.radixdlt.test;


import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.asn1.cms.OtherRecipientInfo;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import utils.CmdHelper;

/**
 * BFT tests against network where all nodes are under synchrony bounds and one or more nodes slow.
 */
@RunWith(Parameterized.class)
@Category(Docker.class)
public class SlowNodeTests {

	private int networkSize;

	public SlowNodeTests(int networkSize){
		this.networkSize = networkSize;
	}

	@Parameters(name = "{index}: given_{0}_correct_bfts_in_latent_docker_network_and_one_slow_node__then_all_instances_should_get_same_commits_and_progress_should_be_made")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][]{
			{3},
			{4}
		});
	}
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

	@Test
	public void Tests() {
		try (DockerNetwork network = DockerNetwork.builder().numNodes(networkSize).build()) {

			network.startBlocking();
			String veth = CmdHelper.getVethByContainerName(network.getNodeIds().stream().findFirst().get());
			CmdHelper.setupQueueQuality(veth);

			RemoteBFTTest test = slowNodeTestBuilder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun()
				.build();
			test.runBlocking(5, TimeUnit.MINUTES);

		}
	}

}
