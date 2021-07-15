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

package com.radixdlt.client.lib.api.action;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.google.common.hash.HashCode;
import com.radixdlt.client.lib.api.ActionType;
import com.radixdlt.client.lib.api.ValidatorAddress;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class UpdateValidatorMetadataAction implements Action {
	private final ActionType type = ActionType.UPDATE_VALIDATOR_METADATA;
	private final ValidatorAddress delegate;
	private final String name;
	private final String url;
	private final HashCode forkVoteHash;

	public UpdateValidatorMetadataAction(ValidatorAddress delegate, String name, String url, HashCode forkVoteHash) {
		this.delegate = delegate;
		this.name = name;
		this.url = url;
		this.forkVoteHash = forkVoteHash;
	}
}
