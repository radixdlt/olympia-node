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

package com.radixdlt.api.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.api.Controller;
import com.radixdlt.api.JsonRpcHandler;
import com.radixdlt.api.controller.AccountController;
import com.radixdlt.api.handler.AccountHandler;
import com.radixdlt.api.qualifier.Account;
import com.radixdlt.api.qualifier.AtArchive;
import com.radixdlt.api.server.JsonRpcServer;

import java.util.Map;

public class AccountEndpointModule extends AbstractModule {
	@AtArchive
	@ProvidesIntoSet
	public Controller accountController(@Account JsonRpcServer jsonRpcServer) {
		return new AccountController(jsonRpcServer);
	}

	@Account
	@Provides
	public JsonRpcServer rpcServer(@Account Map<String, JsonRpcHandler> additionalHandlers) {
		return new JsonRpcServer(additionalHandlers);
	}

	@Account
	@ProvidesIntoMap
	@StringMapKey("account.get_info")
	public JsonRpcHandler accountGetInfo(AccountHandler accountHandler) {
		return accountHandler::handleAccountGetInfo;
	}

	@Account
	@ProvidesIntoMap
	@StringMapKey("account.submit_transaction_single_step")
	public JsonRpcHandler accountSubmitTransactionSingleStep(AccountHandler accountHandler) {
		return accountHandler::handleAccountSubmitTransactionSingleStep;
	}
}
