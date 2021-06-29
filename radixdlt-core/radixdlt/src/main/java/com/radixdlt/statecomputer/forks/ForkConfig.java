/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.statecomputer.forks;

/**
 * Configuration used for hard forks
 */
public final class ForkConfig {
	private final long epoch;
	private final String name;
	private final RERulesConfig config;
	private final RERulesVersion version;

	public ForkConfig(
		long epoch,
		String name,
		RERulesVersion version,
		RERulesConfig config
	) {
		this.epoch = epoch;
		this.name = name;
		this.config = config;
		this.version = version;
	}

	public long getEpoch() {
		return epoch;
	}

	public RERulesVersion getVersion() {
		return version;
	}

	public String getName() {
		return name;
	}

	public RERulesConfig getConfig() {
		return config;
	}

	public ForkConfig overrideEpoch(long epoch) {
		return new ForkConfig(epoch, this.name, this.version, this.config);
	}

	public ForkConfig overrideConfig(RERulesConfig config) {
		return new ForkConfig(this.epoch, this.name, this.version, config);
	}
}
