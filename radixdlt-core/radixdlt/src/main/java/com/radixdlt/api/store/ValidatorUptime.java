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

package com.radixdlt.api.store;

public final class ValidatorUptime {
	private final long proposalsCompleted;
	private final long proposalsMissed;

	private ValidatorUptime(long proposalsCompleted, long proposalsMissed) {
		this.proposalsCompleted = proposalsCompleted;
		this.proposalsMissed = proposalsMissed;
	}

	public static ValidatorUptime create(long proposalsCompleted, long proposalsMissed) {
		return new ValidatorUptime(proposalsCompleted, proposalsMissed);
	}

	public static ValidatorUptime empty() {
		return new ValidatorUptime(0, 0);
	}

	public ValidatorUptime merge(ValidatorUptime other) {
		return new ValidatorUptime(
			this.proposalsCompleted + other.proposalsCompleted,
			this.proposalsMissed + other.proposalsMissed
		);
	}

	public long getProposalsCompleted() {
		return proposalsCompleted;
	}

	public long getProposalsMissed() {
		return proposalsMissed;
	}

	public String toPercentageString() {
		if (proposalsCompleted == 0 && proposalsMissed == 0) {
			return "0.00";
		}
		var uptimePercentage = proposalsCompleted * 10000 / (proposalsCompleted + proposalsMissed);
		var uptimeDouble = uptimePercentage / 100.0;
		return String.format("%.2f", uptimeDouble);
	}

	@Override
	public String toString() {
		return String.format("%s{completed=%s missed=%s}", this.getClass().getSimpleName(), proposalsCompleted, proposalsMissed);
	}
}
