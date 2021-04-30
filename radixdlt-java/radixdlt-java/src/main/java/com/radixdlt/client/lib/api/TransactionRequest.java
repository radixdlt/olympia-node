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

package com.radixdlt.client.lib.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.lib.dto.ActionDTO;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TransactionRequest {
	private final Optional<String> message;
	private final List<ActionDTO> actions;

	private TransactionRequest(Optional<String> message, List<ActionDTO> actions) {
		this.message = message;
		this.actions = actions;
	}

	public TransactionRequestBuilder createBuilder() {
		return new TransactionRequestBuilder();
	}

	@JsonProperty("message")
	public Optional<String> getMessage() {
		return message;
	}

	@JsonProperty("actions")
	public List<ActionDTO> getActions() {
		return actions;
	}

	public static final class TransactionRequestBuilder {
		private final List<ActionDTO> actions = new ArrayList<>();
		private String message;

		private TransactionRequestBuilder() { }

		//TODO: add more actions
		public TransactionRequestBuilder transfer(AccountAddress from, AccountAddress to, UInt256 amount, String rri) {
			actions.add(ActionDTO.transfer(from, to, amount, rri));
			return this;
		}

		public TransactionRequestBuilder stake(AccountAddress from, ValidatorAddress validator, UInt256 amount) {
			actions.add(ActionDTO.stake(from, validator, amount));
			return this;
		}

		public TransactionRequestBuilder unstake(AccountAddress from, ValidatorAddress validator, UInt256 amount) {
			actions.add(ActionDTO.unstake(from, validator, amount));
			return this;
		}

		public TransactionRequestBuilder message(String message) {
			this.message = message;
			return this;
		}

		public TransactionRequest build() {
			return new TransactionRequest(Optional.ofNullable(message), actions);
		}
	}
}
