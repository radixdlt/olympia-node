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

import org.json.JSONObject;
import org.junit.Test;
import org.radix.api.jsonrpc.JsonRpcUtil.RpcError;
import org.radix.api.jsonrpc.handler.AtomHandler;
import org.radix.api.jsonrpc.handler.HighLevelApiHandler;
import org.radix.api.jsonrpc.handler.LedgerHandler;
import org.radix.api.jsonrpc.handler.NetworkHandler;
import org.radix.api.jsonrpc.handler.SystemHandler;
import org.radix.time.Time;

import com.radixdlt.serialization.Serialization;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.response;

public class RadixJsonRpcServerTest {
	@Test
	public void when_send_json_rpc_request_with_no_id__return_json_error_response() {
		var server = new RadixJsonRpcServer(
			mock(SystemHandler.class),
			mock(NetworkHandler.class),
			mock(AtomHandler.class),
			mock(LedgerHandler.class),
			Map.of()
		);

		var response = new JSONObject(server.handleRpc(jsonObject().toString()));

		assertThat(response.getString("jsonrpc")).isEqualTo("2.0");
		assertThat(response.has("result")).isFalse();
		assertThat(response.has("id")).isTrue();
		assertThat(response.isNull("id")).isTrue();
		assertThat(response.getJSONObject("error")).isNotNull();
		assertThat(response.getJSONObject("error").get("code")).isEqualTo(RpcError.INVALID_PARAMS.code());
		assertThat(response.getJSONObject("error").getString("message")).isNotEmpty();
	}

	@Test
	public void when_send_json_rpc_request_ping__return_pong_and_timestamp() {
		var request = jsonObject()
			.put("id", 0)
			.put("method", "Ping")
			.put("params", jsonObject());

		var serializer = mock(Serialization.class);
		when(serializer.toJsonObject(any(), any())).thenAnswer(i -> i.getArguments()[0]);

		var systemHandler = mock(SystemHandler.class);
		var pong = jsonObject().put("response", "pong").put("timestamp", Time.currentTimestamp());
		when(systemHandler.handlePing(any())).thenReturn(response(request, pong));

		var server = new RadixJsonRpcServer(
			systemHandler,
			mock(NetworkHandler.class),
			mock(AtomHandler.class),
			mock(LedgerHandler.class),
			Map.of()
		);

		var response = new JSONObject(server.handleRpc(request.toString()));
		assertThat(response.getString("jsonrpc")).isEqualTo("2.0");
		assertThat(response.has("result")).isTrue();
		assertThat(response.get("id")).isEqualTo(0);

		assertThat(response.getJSONObject("result").get("response")).isEqualTo("pong");
		assertThat(response.getJSONObject("result").has("timestamp")).isTrue();
	}

	@Test
	public void when_send_oversized_json_rpc_request_with__return_json_error_response() {
		var server = new RadixJsonRpcServer(
			mock(SystemHandler.class),
			mock(NetworkHandler.class),
			mock(AtomHandler.class),
			mock(LedgerHandler.class),
			Map.of(),
			5
		);

		var response = new JSONObject(server.handleRpc("123456"));
		assertThat(response.getString("jsonrpc")).isEqualTo("2.0");
		assertThat(response.has("result")).isFalse();
		assertThat(response.has("id")).isTrue();
		assertThat(response.isNull("id")).isTrue();
		assertThat(response.getJSONObject("error")).isNotNull();
		assertThat(response.getJSONObject("error").get("code")).isEqualTo(RpcError.REQUEST_TOO_LONG.code());
		assertThat(response.getJSONObject("error").getString("message")).isNotEmpty();
	}
}
