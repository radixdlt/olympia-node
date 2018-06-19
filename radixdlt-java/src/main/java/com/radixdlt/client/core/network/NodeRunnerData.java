package com.radixdlt.client.core.network;

import com.radixdlt.client.core.atoms.Shards;

public class NodeRunnerData {
	private final String ip;
	private final Shards shards;

	public NodeRunnerData(String ip, long lowShard, long highShard) {
		this.ip = ip;
		this.shards = Shards.range(lowShard, highShard);
	}

	public Shards getShards() {
		return shards;
	}

	public String getIp() {
		return ip;
	}

	@Override
	public String toString() {
		return ip + ": " + shards;
	}

	@Override
	public int hashCode() {
		// TODO: fix hack
		return (ip + shards.toString()).hashCode();
	}

	@Override
	public boolean equals(Object o) {
		NodeRunnerData other = (NodeRunnerData)o;
		return other.ip.equals(ip) && other.shards.equals(this.shards);
	}
}
