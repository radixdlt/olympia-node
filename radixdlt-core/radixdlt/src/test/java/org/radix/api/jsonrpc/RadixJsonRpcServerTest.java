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

import org.junit.Test;
import org.radix.api.jsonrpc.JsonRpcUtil.RpcError;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public class RadixJsonRpcServerTest {
	@Test
	public void when_send_json_rpc_request_with_no_id__return_json_error_response() {
		var server = new RadixJsonRpcServer(Map.of());

		var response = server.handleRpc(jsonObject().toString());

		assertThat(response.getString("jsonrpc")).isEqualTo("2.0");
		assertThat(response.has("result")).isFalse();
		assertThat(response.has("id")).isTrue();
		assertThat(response.isNull("id")).isTrue();
		assertThat(response.getJSONObject("error")).isNotNull();
		assertThat(response.getJSONObject("error").get("code")).isEqualTo(RpcError.INVALID_PARAMS.code());
		assertThat(response.getJSONObject("error").getString("message")).isNotEmpty();
	}

	@Test
	public void when_send_oversized_json_rpc_request_with__return_json_error_response() {
		var server = new RadixJsonRpcServer(Map.of(), 5);

		var response = server.handleRpc("123456");
		assertThat(response.getString("jsonrpc")).isEqualTo("2.0");
		assertThat(response.has("result")).isFalse();
		assertThat(response.has("id")).isTrue();
		assertThat(response.isNull("id")).isTrue();
		assertThat(response.getJSONObject("error")).isNotNull();
		assertThat(response.getJSONObject("error").get("code")).isEqualTo(RpcError.REQUEST_TOO_LONG.code());
		assertThat(response.getJSONObject("error").getString("message")).isNotEmpty();
	}
}
