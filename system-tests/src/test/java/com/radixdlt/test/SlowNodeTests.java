package com.radixdlt.test;


import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import utils.CmdHelper;

/**
 * BFT tests against network where all nodes are under synchrony bounds and one or more nodes slow.
 */
public class SlowNodeTests {
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
	@Category(Docker.class)
	public void given_3_correct_bfts_in_latent_docker_network_and_one_slow_node__then_all_instances_should_get_same_commits_and_progress_should_be_made() {
		try (DockerNetwork network = DockerNetwork.builder().numNodes(3).build()) {

			network.startBlocking();
			Thread.sleep(20000);
			CmdHelper.runCommand("docker run --rm --pid=host --privileged  "
				+ "-v /var/run/docker.sock:/var/run/docker.sock dockerinpractice/comcast "
				+ "-cont "+ network.getNodeIds().stream().findFirst().get() +" -default-bw 50 -latency 100 -packet-loss 20%");
			CmdHelper.runCommand("docker run --rm --pid=host --privileged  "
				+ "-v /var/run/docker.sock:/var/run/docker.sock dockerinpractice/comcast "
				+ "-cont "+ network.getNodeIds().stream().findFirst().get() +" -stop");
			CmdHelper.runCommand("docker run --rm --pid=host --privileged  "
				+ "-v /var/run/docker.sock:/var/run/docker.sock dockerinpractice/comcast "
				+ "-cont "+ network.getNodeIds().stream().findFirst().get() +" -default-bw 50 -latency 100 -packet-loss 20%");
			RemoteBFTTest test = slowNodeTestBuilder()
				.network(RemoteBFTNetworkBridge.of(network))
				.waitUntilResponsive()
				.startConsensusOnRun()
				.build();
			test.runBlocking(20, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
