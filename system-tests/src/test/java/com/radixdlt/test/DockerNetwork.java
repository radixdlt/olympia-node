/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.test;

import java.util.Optional;
import okhttp3.HttpUrl;
import utils.CmdHelper;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A Docker-backed implementation of a {@link RemoteBFTNetwork}. Upon construction an instance of this class
 * automatically sets up a local Docker network with the configured arguments. When an instance is closed,
 * the underlying Docker network is shut down gracefully.
 * <p>
 * This class it NOT thread-safe.
 * <p>
 * Note that successful Docker setup requires the tag 'radixdlt/radixdlt-core:develop' to be present.
 */
public class DockerNetwork implements Closeable, RemoteBFTNetwork {
	private static final String OPTIONS_KEY_PORT = "hostPort";
	private static final String NETWORK = "network";
	private static final String DID_NETWORK = "DID"; //Docker in docker network on jenkins

	private final String name;
	private final int numNodes;
	private final boolean startConsensusOnBoot;
	private final String testName;
	private NetworkState networkState;

	private Map<String, Map<String, Object>> dockerOptionsPerNode;

	private DockerNetwork(String name, int numNodes, boolean startConsensusOnBoot,String testName) {
		this.name = Objects.requireNonNull(name);
		this.numNodes = numNodes;
		this.startConsensusOnBoot = startConsensusOnBoot;
		this.networkState = NetworkState.READY;
		this.testName = testName;
	}

	/**
	 * Sets up and runs the Docker network as configured, blocking until the network has been set up.
	 * In case this network cannot be started, throws an IllegalStateException.
	 * <p>
	 * Note that This will also kill any other network with the same name (but not networks with a different name)
	 * as well as kill all active docker contains.
	 */
	public void startBlocking() {
		this.networkState.assertCanStart();
		this.dockerOptionsPerNode = setupBlocking(this.name, this.numNodes, this.startConsensusOnBoot);
		this.networkState = NetworkState.STARTED;
	}

	/**
	 * Sets up and runs the Docker network, storing the data required to maintain it.
	 * This method completes when the network has been set up and the instance constructed.
	 * <p>
	 * Note that This will also kill any other network with the same name (but not networks with a different name)
	 * as well as kill all active docker contains.
	 *
	 * @param networkName          The name of the network
	 * @param numNodes             The number of nodes
	 * @param startConsensusOnBoot Whether to start consensus on boot or wait for a start signal
	 * @return The per-node docker options
	 */
	private static Map<String, Map<String, Object>> setupBlocking(String networkName,
	                                                              int numNodes,
	                                                              boolean startConsensusOnBoot) {
		Map<String, Map<String, Object>> dockerOptionsPerNode = CmdHelper.getDockerOptions(numNodes, startConsensusOnBoot);
		CmdHelper.removeAllDockerContainers(); // TODO do we need  if yes, document it
		String[] universeValidatorEnvVariables = CmdHelper.generateUniverseValidators(numNodes);
		if(!CmdHelper.testRunningOnDocker() && !networkName.contains(DID_NETWORK) ){
			System.out.println(" Network is " + networkName);
			CmdHelper.runCommand("docker network rm " + networkName);
			CmdHelper.runCommand("docker network create " + networkName, null, true);
		}
		dockerOptionsPerNode.forEach((nodeId, options) -> {
			options.put("network", networkName);
			String nodeValidatorKey = CmdHelper.getNodeValidator(universeValidatorEnvVariables,options);
			String universe = CmdHelper.getUniverse(universeValidatorEnvVariables);
			List<Object> dockerSetup = CmdHelper.node(options,universe,nodeValidatorKey);
			String[] dockerEnv = (String[]) dockerSetup.get(0);
			String dockerCommand = (String) dockerSetup.get(1);
			String containerId = CmdHelper.runContainer(dockerCommand, dockerEnv);
			options.put("containerId",containerId);
		});

		return Collections.unmodifiableMap(dockerOptionsPerNode);
	}

	@Override
	public void close() {
		this.networkState.assertCanShutdown();
		this.dockerOptionsPerNode.forEach((nodeId,options)->{
			String containerId = (String) options.get("containerId");
			CmdHelper.captureLogs(containerId,testName);
		});
		CmdHelper.removeAllDockerContainers();
		CmdHelper.cleanCoreGradleOutput();
		CmdHelper.runCommand("docker network rm " + this.name);
		this.networkState = NetworkState.SHUTDOWN;
	}


	@Override
	public HttpUrl getEndpointUrl(String nodeId, String endpoint) {
		this.networkState.assertCanUse();
		return HttpUrl.parse(getNodeEndpoint(nodeId, endpoint));
	}

	private String getNodeEndpoint(String nodeId, String endpoint) {
		return getNodeEndpoint(this.dockerOptionsPerNode.get(nodeId), endpoint);
	}

	// utility for getting the API endpoint (as a string) out of generated node options
	private static String getNodeEndpoint(Map<String, Object> nodeOptions, final String endpoint) {
		int nodePort = (Integer) nodeOptions.get(OPTIONS_KEY_PORT);
		String network = (String) nodeOptions.get(NETWORK);
		return network.contains(DID_NETWORK) || CmdHelper.testRunningOnDocker() ?
			String.format("http://%s:8080/%s", nodeOptions.get("nodeName"), endpoint):
			String.format("http://localhost:%d/%s", nodePort, endpoint);
	}

	@Override
	public Set<String> getNodeIds() {
		this.networkState.assertCanUse();
		return this.dockerOptionsPerNode.keySet();
	}

	public String getName() {
		return this.name;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * A builder for {@link DockerNetwork}s
	 */
	public static class Builder {
		private static AtomicInteger networkIdCounter = new AtomicInteger(0);
		private String name = Optional.ofNullable(System.getenv("TEST_NETWORK"))
			.orElse("test-network-" + networkIdCounter.getAndIncrement());
		private int numNodes = -1;
		private boolean startConsensusOnBoot;
		private String testName;

		/**
		 * Configures the nodes to automatically start consensus as soon as they boot up.
		 * Note that this may cause nodes to initially be out of sync as some nodes may take longer to boot.
		 *
		 * @return This builder
		 */
		public Builder startConsensusOnBoot() {
			this.startConsensusOnBoot = true;
			return this;
		}

		/**
		 * Sets a certain name to used for the Docker network
		 *
		 * @param name The name
		 * @return This builder
		 */
		public Builder name(String name) {
			this.name = Objects.requireNonNull(name);
			return this;
		}

		/**
		 * Sets the number of nodes to build in this network
		 *
		 * @param numNodes The number of nodes
		 * @return This builder
		 */
		public Builder numNodes(int numNodes) {
			if (numNodes < 1) {
				throw new IllegalArgumentException("numNodes must be >= 1 but was " + numNodes);
			}
			this.numNodes = numNodes;
			return this;
		}

		/**
		 * Sets the name of the test that is running the network
		 *
		 * @param name The number of nodes
		 * @return This builder
		 */
		public Builder testName(String name) {
			this.testName = name;
			return this;
		}

		/**
		 * Builds a {@link DockerNetwork} with the specified configuration without running the underlying network.
		 *
		 * @return The created {@link DockerNetwork}
		 */
		public DockerNetwork build() {
			if (numNodes == -1) {
				throw new IllegalStateException("numNodes was not set");
			}

			return new DockerNetwork(name, numNodes, startConsensusOnBoot,testName);
		}
	}

	/**
	 * Simple representation of the internal network state used to ensure safe transitions.
	 */
	private enum NetworkState {
		READY,
		STARTED,
		SHUTDOWN;

		private void assertCan(String action, NetworkState expectedState) {
			if (this != expectedState) {
				throw new IllegalStateException(String.format(
					"cannot %s, current state is: %s (must be %s)",
					action, this, expectedState));
			}
		}

		private void assertCanStart() {
			assertCan("start", READY);
		}

		private void assertCanUse() {
			assertCan("use", STARTED);
		}

		private void assertCanShutdown() {
			assertCan("shutdown", STARTED);
		}
	}
}
