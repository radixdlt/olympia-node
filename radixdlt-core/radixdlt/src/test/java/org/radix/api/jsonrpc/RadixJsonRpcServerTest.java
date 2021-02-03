/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package org.radix.api.jsonrpc;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.EpochManagerRunner;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.universe.Universe;
import org.json.JSONObject;
import org.junit.Test;
import org.radix.api.services.AtomsService;

import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.serialization.Serialization;

import org.radix.universe.system.LocalSystem;

public class RadixJsonRpcServerTest {
	@Test
	public void when_send_json_rpc_request_with_no_id__return_json_error_response() {
		JSONObject request = new JSONObject();

		RadixJsonRpcServer server = new RadixJsonRpcServer(
			rmock(EventDispatcher.class),
			mock(EpochManagerRunner.class),
			mock(Serialization.class),
			mock(LedgerEntryStore.class),
			mock(AtomsService.class),
			mock(LocalSystem.class),
			mock(AddressBook.class),
			mock(Universe.class)
		);

		JSONObject response = new JSONObject(server.handleChecked(request.toString()));
		assertThat(response.getString("jsonrpc")).isEqualTo("2.0");
		assertThat(response.has("result")).isFalse();
		assertThat(response.has("id")).isTrue();
		assertThat(response.isNull("id")).isTrue();
		assertThat(response.getJSONObject("error")).isNotNull();
		assertThat(response.getJSONObject("error").get("code")).isEqualTo(JsonRpcUtil.INVALID_REQUEST_CODE);
		assertThat(response.getJSONObject("error").getString("message")).isNotEmpty();
	}

	@Test
	public void when_send_json_rpc_request_ping__return_pong_and_timestamp() {
		JSONObject request = new JSONObject()
			.put("id", 0)
			.put("method", "Ping")
			.put("params", new JSONObject());

		Serialization serializer = mock(Serialization.class);
		when(serializer.toJsonObject(any(), any())).thenAnswer(i -> i.getArguments()[0]);

		RadixJsonRpcServer server = new RadixJsonRpcServer(
			rmock(EventDispatcher.class),
			mock(EpochManagerRunner.class),
			serializer,
			mock(LedgerEntryStore.class),
			mock(AtomsService.class),
			mock(LocalSystem.class),
			mock(AddressBook.class),
			mock(Universe.class));

		JSONObject response = new JSONObject(server.handleChecked(request.toString()));
		assertThat(response.getString("jsonrpc")).isEqualTo("2.0");
		assertThat(response.has("result")).isTrue();
		assertThat(response.get("id")).isEqualTo(0);

		assertThat(response.getJSONObject("result").get("response")).isEqualTo("pong");
		assertThat(response.getJSONObject("result").has("timestamp")).isTrue();
	}

	@Test
	public void when_send_oversized_json_rpc_request_with__return_json_error_response() {
		RadixJsonRpcServer server = new RadixJsonRpcServer(
			rmock(EventDispatcher.class),
			mock(EpochManagerRunner.class),
			mock(Serialization.class),
			mock(LedgerEntryStore.class),
			mock(AtomsService.class),
			mock(LocalSystem.class),
			mock(AddressBook.class),
			mock(Universe.class),
			5
		);

		JSONObject response = new JSONObject(server.handleChecked("123456"));
		assertThat(response.getString("jsonrpc")).isEqualTo("2.0");
		assertThat(response.has("result")).isFalse();
		assertThat(response.has("id")).isTrue();
		assertThat(response.isNull("id")).isTrue();
		assertThat(response.getJSONObject("error")).isNotNull();
		assertThat(response.getJSONObject("error").get("code")).isEqualTo(JsonRpcUtil.OVERSIZED_REQUEST);
		assertThat(response.getJSONObject("error").getString("message")).isNotEmpty();
	}
}