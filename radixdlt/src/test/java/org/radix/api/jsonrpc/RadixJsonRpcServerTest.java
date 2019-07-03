package org.radix.api.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import org.everit.json.schema.Schema;
import org.json.JSONObject;
import org.junit.Test;
import org.radix.api.services.AtomsService;
import org.radix.atoms.AtomStore;
import com.radixdlt.serialization.Serialization;
import org.radix.atoms.sync.AtomSync;

public class RadixJsonRpcServerTest {
	@Test
	public void when_send_json_rpc_request_with_no_id__return_json_error_response() {
		JSONObject request = new JSONObject();

		RadixJsonRpcServer server = new RadixJsonRpcServer(
			mock(Serialization.class),
			mock(AtomStore.class),
			mock(AtomSync.class),
			mock(AtomsService.class),
			mock(Schema.class)
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
				serializer,
				mock(AtomStore.class),
				mock(AtomSync.class),
				mock(AtomsService.class),
				mock(Schema.class)
		);

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
			mock(Serialization.class),
			mock(AtomStore.class),
			mock(AtomSync.class),
			mock(AtomsService.class),
			mock(Schema.class),
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