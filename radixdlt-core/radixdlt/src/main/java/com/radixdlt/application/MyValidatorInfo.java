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

package com.radixdlt.application;

import com.radixdlt.identifiers.REAddr;

import java.util.Optional;

/**
 * Info about node as validator
 */
public final class MyValidatorInfo {
	private final String name;
	private final String url;
	private final boolean registered;
	private final int currentRake;
	private final boolean allowDelegation;
	private final REAddr owner;

	public MyValidatorInfo(
		String name, String url, boolean registered, int currentRake, boolean allowDelegation, REAddr owner
	) {
		this.name = name;
		this.url = url;
		this.registered = registered;
		this.currentRake = currentRake;
		this.allowDelegation = allowDelegation;
		this.owner = owner;
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}

	public boolean isRegistered() {
		return registered;
	}

	public int getCurrentRake() {
		return currentRake;
	}

	public boolean allowsDelegation() {
		return allowDelegation;
	}

	public Optional<REAddr> getOwner() {
		return Optional.ofNullable(owner);
	}

	public MyValidatorInfo withNameUrlAndRegistration(String name, String url, boolean registered) {
		return new MyValidatorInfo(name, url, registered, currentRake, allowDelegation, owner);
	}

	public MyValidatorInfo withRake(int currentRake) {
		return new MyValidatorInfo(name, url, registered, currentRake, allowDelegation, owner);
	}

	public MyValidatorInfo withOwner(REAddr owner) {
		return new MyValidatorInfo(name, url, registered, currentRake, allowDelegation, owner);
	}

	public MyValidatorInfo withDelegation(boolean allowDelegation) {
		return new MyValidatorInfo(name, url, registered, currentRake, allowDelegation, owner);
	}
}
