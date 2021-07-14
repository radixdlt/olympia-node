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
import com.radixdlt.client.lib.api.ActionType;
import com.radixdlt.crypto.ECPublicKey;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class CreateMutableTokenAction implements Action {
	private final ActionType type = ActionType.CREATE_MUTABLE;
	private final ECPublicKey publicKeyOfSigner;
	private final String symbol;
	private final String name;
	private final String description;
	private final String iconUrl;
	private final String tokenUrl;

	public CreateMutableTokenAction(
		ECPublicKey publicKeyOfSigner, String symbol, String name,
		String description, String iconUrl, String tokenUrl
	) {
		this.publicKeyOfSigner = publicKeyOfSigner;
		this.symbol = symbol;
		this.name = name;
		this.description = description;
		this.iconUrl = iconUrl;
		this.tokenUrl = tokenUrl;
	}
}
