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

import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.middleware2.converters.AtomConversionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.radix.api.observable.Disposable;
import org.radix.api.services.AtomStatusListener;
import org.radix.api.services.AtomsService;

/**
 * Epic used to manage streaming status notifications regarding an atom.
 */
public class AtomStatusEpic {
	/**
	 * Interface for atom submission and return of results
	 */
	private final AtomsService atomsService;

	/**
	 * Stream of JSON RPC objects to be sent back in the same channel
	 */
	private final Consumer<JSONObject> callback;

	private final ConcurrentHashMap<String, Disposable> observers = new ConcurrentHashMap<>();

	public AtomStatusEpic(AtomsService atomsService, Consumer<JSONObject> callback) {
		this.atomsService = atomsService;
		this.callback = callback;
	}

	public void action(JSONObject jsonRequest) {
		final Object id = jsonRequest.get("id");
		final JSONObject params = jsonRequest.getJSONObject("params");
		final String subscriberId = jsonRequest.getJSONObject("params").getString("subscriberId");

		if (jsonRequest.getString("method").equals("Atoms.closeAtomStatusNotifications")) {
			Disposable disposable = observers.remove(subscriberId);
			if (disposable != null) {
				disposable.dispose();
			}

			callback.accept(JsonRpcUtil.simpleResponse(id, "success", true));
			return;
		}

		AID aid = AID.from(params.getString("aid"));
		callback.accept(JsonRpcUtil.simpleResponse(id, "success", true));

		final BiConsumer<AtomStatus, JSONObject> sendAtomSubmissionState = (status, data) -> {
			JSONObject responseParams = new JSONObject();
			responseParams.put("subscriberId", subscriberId);
			responseParams.put("status", status.toString());
			if (data != null) {
				responseParams.put("data", data);
			}
			JSONObject notification = new JSONObject();
			notification.put("jsonrpc", "2.0");
			notification.put("method", "Atoms.nextStatusEvent");
			notification.put("params", responseParams);
			callback.accept(notification);
		};

		Disposable disposable = atomsService.subscribeAtomStatusNotifications(aid, new AtomStatusListener() {
			@Override
			public void onStored(CommittedAtom committedAtom) {
				JSONObject data = new JSONObject();
				data.put("aid", committedAtom.getAID());
				// TODO: serialize vertexMetadata
				VerifiedLedgerHeaderAndProof ledgerState = committedAtom.getStateAndProof();
				data.put("stateVersion", ledgerState.getStateVersion());
				data.put("epoch", ledgerState.getEpoch());
				data.put("timestamp", ledgerState.timestamp());

				sendAtomSubmissionState.accept(AtomStatus.STORED, data);
			}

			@Override
			public void onError(Throwable e) {
				if (e instanceof AtomConversionException) {
					AtomConversionException conversionException = (AtomConversionException) e;
					String pointerToIssue = conversionException.getPointerToIssue();

					JSONObject data = new JSONObject();
					data.put("message", e.getMessage());
					data.put("pointerToIssue", pointerToIssue);
					sendAtomSubmissionState.accept(AtomStatus.EVICTED_FAILED_CM_VERIFICATION, data);
				} else if (e instanceof RadixEngineException) {
					RadixEngineException reException = (RadixEngineException) e;
					String pointerToIssue = reException.getDataPointer().toString();

					JSONObject data = new JSONObject();
					data.put("aid", aid);
					data.put("message", reException.getMessage());
					data.put("errorCode", reException.getErrorCode().toString());
					data.put("pointerToIssue", pointerToIssue);

					final AtomStatus atomStatus;
					switch (reException.getErrorCode()) {
						case CONVERSION_ERROR:
						// Fall through
						case CM_ERROR:
						// Fall through
						case HOOK_ERROR:
							if (reException.getCmError() != null) {
								data.put("cmError", reException.getCmError().getErrMsg());
							}
							atomStatus = AtomStatus.EVICTED_FAILED_CM_VERIFICATION;
							break;

						case VIRTUAL_STATE_CONFLICT:
							atomStatus = AtomStatus.EVICTED_FAILED_CM_VERIFICATION;
							break;
						case MISSING_DEPENDENCY:
							atomStatus = AtomStatus.MISSING_DEPENDENCY;
							break;
						case STATE_CONFLICT:
							atomStatus = AtomStatus.CONFLICT_LOSER;
							break;
						default: // Don't send back unhandled exception
							return;
					}

					sendAtomSubmissionState.accept(atomStatus, data);
				} else if (e instanceof MempoolFullException) {
					JSONObject data = new JSONObject();
					data.put("message", e.getMessage());
					// FIXME: Probably should be something different here, but decision deferred until later
					sendAtomSubmissionState.accept(AtomStatus.DOES_NOT_EXIST, data);
				} else if (e instanceof MempoolDuplicateException) {
					JSONObject data = new JSONObject();
					data.put("message", e.getMessage());
					// FIXME: Probably should be something different here, but decision deferred until later
					sendAtomSubmissionState.accept(AtomStatus.CONFLICT_LOSER, data);
				} else {
					JSONObject data = new JSONObject();
					data.put("message", e.getMessage());

					sendAtomSubmissionState.accept(null, data);
				}
			}
		});

		final Disposable lastDisposable = observers.remove(subscriberId);
		if (lastDisposable != null) {
			lastDisposable.dispose();
		}
		observers.put(subscriberId, disposable);
	}

	public void dispose() {
		observers.forEachKey(100, subscriberId -> observers.remove(subscriberId).dispose());
	}
}
