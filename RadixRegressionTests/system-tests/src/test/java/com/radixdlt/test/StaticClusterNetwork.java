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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import okhttp3.HttpUrl;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import utils.TestnetNodes;

/**
 * An unmanaged, static implementation of a {@link RemoteBFTNetwork} backed by a static cluster of remote node URLs.
 * <p>
 * Note that unmanaged implies that the lifecycle of the network and the nodes within it is not managed here.
 */
// TODO ideally should be able to manage a clusters lifecycle the same way a docker net is managed
public class StaticClusterNetwork implements RemoteBFTNetwork {
	private static final String STATIC_CLUSTER_NODE_URLS_PROPERTY = "clusterNodeUrls";
	private static final String STATIC_CLUSTER_TESTNET_NAME = "TESTNET_NAME";

	private final Set<String> nodeUrls;
	private String clusterName;

	private StaticClusterNetwork(Set<String> nodeUrls) {
		this.nodeUrls = nodeUrls;
	}

	private StaticClusterNetwork(Set<String> nodeUrls, String clusterName) {
		this.nodeUrls = nodeUrls;
		this.clusterName = clusterName;
	}

	@Override
	public HttpUrl getEndpointUrl(String nodeId, String endpoint) {
		if (!nodeUrls.contains(nodeId)) {
			throw new IllegalArgumentException("unknown nodeId: " + nodeId);
		}

		String endpointUrl = String.format("%s/%s", nodeId, endpoint);
		return HttpUrl.parse(endpointUrl);
	}

	@Override
	public Set<String> getNodeIds() {
		return nodeUrls; // TODO is using node URLs as their ids fine?
	}

	public String getClusterName() {
		if(clusterName == null){
			throw new IllegalStateException("Cluster Name is empty. Its the name of remote cluster");
		}
		return clusterName;
	}
	/**
	 * Creates a static BFT network from a set of nodeUrls.
	 * <p>
	 * Note that the nodes are not actively managed in any way and are assumed to be operational.
	 *
	 * @param nodeUrls The node URLs
	 * @return A static BFT network comprising the given ndoes
	 */
	public static StaticClusterNetwork from(Iterable<String> nodeUrls) {
		Objects.requireNonNull(nodeUrls);
		ImmutableSet<String> nodeIpsSet = ImmutableSet.copyOf(nodeUrls);
		if (nodeIpsSet.isEmpty()) {
			throw new IllegalArgumentException("network must contain at least one node ip");
		}
		return new StaticClusterNetwork(nodeIpsSet);
	}

	/**
	 * Creates a static cluster BFT network of the cluster defined in the STATIC_CLUSTER_NODE_URLS_PROPERTY.
	 * This method will fail if the number of non-empty URLs does not match the expected count.
	 *
	 * @param expectedNumNodes The expected number of nodes
	 * @return A static cluster network
	 */
	public static StaticClusterNetwork extractFromProperty(int expectedNumNodes) {
		ImmutableList<String> nodeUrls = getClusterNodeUrls(expectedNumNodes);
		return StaticClusterNetwork.from(nodeUrls);
	}


	public static StaticClusterNetwork extractFromTestnet(int expectedNumNodes){
		ImmutableSet<String> nodesList = new TestnetNodes().nodeURLList();
		return new StaticClusterNetwork(nodesList,System.getenv(STATIC_CLUSTER_TESTNET_NAME));
	}

	public static StaticClusterNetwork extractFromTestnet(int expectedNumNodes,String dockerOptions,String cmdOptions){
		ImmutableSet<String> nodesList = new TestnetNodes()
			.usingDockerRunOptions(dockerOptions)
			.usingCmdOptions(cmdOptions)
			.nodeURLList();
		return new StaticClusterNetwork(nodesList,System.getenv(STATIC_CLUSTER_TESTNET_NAME));
	}
	/**
	 * Creates a static cluster BFT network of the cluster .
	 * Based on whether static cluster name is avaliable as environment variable it uses ansible to fetch nodes information.
	 * If static cluster name is not avaliable, it will fetch nodes using system property variable
	 * @param expectedNumNodes The expected number of nodes
	 * @return A static cluster network
	 */
	public static StaticClusterNetwork clusterInfo(int expectedNumNodes){
		if(System.getenv(STATIC_CLUSTER_TESTNET_NAME) == null ){
			return StaticClusterNetwork.extractFromProperty(expectedNumNodes);
		}else{
			return StaticClusterNetwork.extractFromTestnet(expectedNumNodes);
		}
	}

	public static StaticClusterNetwork clusterInfo(int expectedNumNodes,String dockerOptions,String cmdOptions){
		return StaticClusterNetwork.extractFromTestnet(expectedNumNodes,dockerOptions,cmdOptions);
	}
	/**
	 * Extracts cluster node URLs out of the STATIC_CLUSTER_NODE_URLS_PROPERTY.
	 * This method will fail if the number of non-empty URLs does not match the expected count.
	 *
	 * @param expectedNumNodes The expected number of node URLs
	 * @return A list of non-empty node urls as set in STATIC_CLUSTER_NODE_URLS_PROPERTY
	 */
	private static ImmutableList<String> getClusterNodeUrls(int expectedNumNodes) {
		ImmutableList<String> clusterNodeUrls = Arrays.stream(System.getProperty(STATIC_CLUSTER_NODE_URLS_PROPERTY, "")
			.split(","))
			.filter(url -> !url.isEmpty())
			.collect(ImmutableList.toImmutableList());
		if (expectedNumNodes != clusterNodeUrls.size()) {
			throw new IllegalStateException(String.format(
				"system property %s set with %d comma-separated node urls",
				STATIC_CLUSTER_NODE_URLS_PROPERTY, expectedNumNodes)
			);
		}
		return clusterNodeUrls;
	}
}
