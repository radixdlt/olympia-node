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

import com.radixdlt.engine.RadixEngineException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.radixdlt.identifiers.AID;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;

import org.json.JSONObject;
import org.radix.api.services.AtomsService;
import org.radix.api.services.SingleAtomListener;

/**
 * Epic responsible for translating an atom submission JSON RPC request to the response and resulting
 * notifications.
 *
 * TODO: utilize a framework like akka to deal with asynchronicity
 */
public class SubmitAtomAndSubscribeEpic {

	/**
	 * States an atom submission can be in
	 */
	public enum AtomSubmissionState {
		SUBMITTING, SUBMITTED, STORED, VALIDATION_ERROR, UNKNOWN_ERROR, COLLISION, UNSUITABLE_PEER
	}

	/**
	 * Interface for atom submission and return of results
	 */
	private final AtomsService atomsService;

	/**
	 * Stream of JSON RPC objects to be sent back in the same channel
	 */
	private final Consumer<JSONObject> callback;

	public SubmitAtomAndSubscribeEpic(AtomsService atomsService, Consumer<JSONObject> callback) {
		this.atomsService = atomsService;
		this.callback = callback;
	}

	public void action(JSONObject jsonMethod) {
		Object id = jsonMethod.get("id");
		JSONObject params = jsonMethod.getJSONObject("params");
		Object subscriberId = jsonMethod.getJSONObject("params").get("subscriberId");
		JSONObject jsonAtom = params.getJSONObject("atom");


		final BiConsumer<AtomSubmissionState, JSONObject> sendAtomSubmissionState = (state, data) -> {
			JSONObject responseParams = new JSONObject();
			responseParams.put("subscriberId", subscriberId);
			responseParams.put("value", state.toString());
			if (data != null) {
				responseParams.put("data", data);
			}
			JSONObject notification = new JSONObject();
			notification.put("jsonrpc", "2.0");
			notification.put("method", "AtomSubmissionState.onNext");
			notification.put("params", responseParams);
			callback.accept(notification);
		};

		SingleAtomListener subscriber = new SingleAtomListener() {
			@Override
			public void onStored() {
				JSONObject data = new JSONObject();
				sendAtomSubmissionState.accept(AtomSubmissionState.STORED, data);
			}

			@Override
			public void onStoredFailure(RadixEngineException exception) {
				JSONObject data = new JSONObject();
				data.put("pointerToIssue", exception.getDataPointer().toString());
				final AtomSubmissionState atomSubmissionState;
				switch (exception.getErrorCode()) {
					case VIRTUAL_STATE_CONFLICT:
					case MISSING_DEPENDENCY:
						atomSubmissionState = AtomSubmissionState.VALIDATION_ERROR;
						break;
					case STATE_CONFLICT:
						atomSubmissionState = AtomSubmissionState.COLLISION;
						break;
					default: // Don't send back unhandled exception
						return;
				}

				sendAtomSubmissionState.accept(atomSubmissionState, data);
			}

			@Override
			public void onError(AID atomId, Throwable e) {
				if (e instanceof MempoolFullException) {
					JSONObject data = new JSONObject();
					data.put("message", e.getMessage());
					// FIXME: Probably should be something different here, but decision deferred until later
					sendAtomSubmissionState.accept(AtomSubmissionState.UNSUITABLE_PEER, data);
				} else if (e instanceof MempoolDuplicateException) {
					JSONObject data = new JSONObject();
					data.put("message", e.getMessage());
					data.put("pointerToIssue", DataPointer.ofAtom());
					sendAtomSubmissionState.accept(AtomSubmissionState.COLLISION, data);
				} else {
					JSONObject data = new JSONObject();
					data.put("message", e.getMessage());

					sendAtomSubmissionState.accept(AtomSubmissionState.UNKNOWN_ERROR, data);
				}
			}
		};

		try {
			atomsService.submitAtom(jsonAtom, subscriber);
		} catch (IllegalArgumentException e) {
			callback.accept(JsonRpcUtil.errorResponse(id, -32000, e.getMessage()));
		}
	}
}
