/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.api.services;

import org.json.JSONObject;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.ModuleRunner;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public class SystemService {
	private final Serialization serialization;
	private final Universe universe;
	private final LocalSystem localSystem;
	private final ModuleRunner consensusRunner;

	public SystemService(
		final Serialization serialization,
		final Universe universe,
		final LocalSystem localSystem,
		final ModuleRunner consensusRunner
	) {
		this.serialization = serialization;
		this.universe = universe;
		this.localSystem = localSystem;
		this.consensusRunner = consensusRunner;
	}

	public JSONObject getUniverse() {
		return serialization.toJsonObject(universe, DsonOutput.Output.API);
	}

	public JSONObject getLocalSystem() {
		return serialization.toJsonObject(localSystem, DsonOutput.Output.API);
	}

	public JSONObject getPong() {
		return jsonObject().put("response", "pong").put("timestamp", Time.currentTimestamp());
	}

	public JSONObject bftStart() {
		consensusRunner.start();
		return jsonObject().put("response", "success");
	}

	public JSONObject bftStop() {
		consensusRunner.stop();
		return jsonObject().put("response", "success");
	}
}
