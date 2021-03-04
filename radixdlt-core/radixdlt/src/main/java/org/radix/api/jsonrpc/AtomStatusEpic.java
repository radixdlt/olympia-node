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
import org.radix.api.observable.Disposable;
import org.radix.api.services.AtomStatusListener;
import org.radix.api.services.AtomsService;

import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.MempoolDuplicateException;
import com.radixdlt.mempool.MempoolFullException;
import com.radixdlt.middleware2.converters.AtomConversionException;
import com.radixdlt.statecomputer.CommittedAtom;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.radixdlt.statecomputer.RadixEngineMempoolException;
import static org.radix.api.jsonrpc.AtomStatus.*;
import static org.radix.api.jsonrpc.AtomStatus.EVICTED_FAILED_CM_VERIFICATION;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.notification;
import static org.radix.api.jsonrpc.JsonRpcUtil.simpleResponse;

/**
 * Epic used to manage streaming status notifications regarding an atom.
 */
public class AtomStatusEpic {
	private final AtomsService atomsService;
	private final Consumer<JSONObject> callback;
	private final ConcurrentHashMap<String, Disposable> observers = new ConcurrentHashMap<>();

	public AtomStatusEpic(AtomsService atomsService, Consumer<JSONObject> callback) {
		this.atomsService = atomsService;
		this.callback = callback;
	}

	public void action(JSONObject jsonRequest) {
		final var id = jsonRequest.get("id");
		final var params = jsonRequest.getJSONObject("params");
		final var subscriberId = params.getString("subscriberId");

		if (jsonRequest.getString("method").equals("Atoms.closeAtomStatusNotifications")) {
			Optional.ofNullable(observers.remove(subscriberId))
				.ifPresent(Disposable::dispose);

			callback.accept(simpleResponse(id, "success", true));
			return;
		}

		var aid = AID.from(params.getString("aid"));
		callback.accept(simpleResponse(id, "success", true));

		Optional.ofNullable(observers.remove(subscriberId)).ifPresent(Disposable::dispose);

		observers.put(
			subscriberId,
			atomsService.subscribeAtomStatusNotifications(aid, new EpicAtomStatusListener(makeCallback(subscriberId), aid))
		);
	}

	private BiConsumer<AtomStatus, JSONObject> makeCallback(final String subscriberId) {
		return (status, data) -> {
			var responseParams = jsonObject()
				.put("subscriberId", subscriberId)
				.put("status", status.toString());

			if (data != null) {
				responseParams.put("data", data);
			}

			callback.accept(notification("Atoms.nextStatusEvent", responseParams));
		};
	}

	public void dispose() {
		observers.forEachKey(100, subscriberId -> observers.remove(subscriberId).dispose());
	}

	private static class EpicAtomStatusListener implements AtomStatusListener {
		private final BiConsumer<AtomStatus, JSONObject> sendAtomSubmissionState;
		private final AID aid;

		public EpicAtomStatusListener(final BiConsumer<AtomStatus, JSONObject> sendAtomSubmissionState, final AID aid) {
			this.sendAtomSubmissionState = sendAtomSubmissionState;
			this.aid = aid;
		}

		@Override
		public void onStored(CommittedAtom committedAtom) {
			var headerAndProof = committedAtom.getHeaderAndProof();

			sendAtomSubmissionState.accept(STORED, jsonObject()
				.put("aid", committedAtom.getAID())
				.put("stateVersion", headerAndProof.getStateVersion())
				.put("epoch", headerAndProof.getEpoch())
				.put("timestamp", headerAndProof.timestamp()));
		}

		@Override
		public void onError(Throwable e) {
			if (e instanceof AtomConversionException) {
				sendAtomSubmissionState.accept(
					EVICTED_FAILED_CM_VERIFICATION,
					fromException(e).put("pointerToIssue", ((AtomConversionException) e).getPointerToIssue())
				);
			} else if (e instanceof RadixEngineMempoolException) {
				var exception = (RadixEngineMempoolException) e;
				var reException = exception.getException();

				var data = fromException(e)
					.put("aid", aid)
					.put("errorCode", reException.getErrorCode().toString())
					.put("pointerToIssue", reException.getDataPointer().toString());

				Optional.ofNullable(extractAtomStatus(reException, data))
					.ifPresent(atomStatus -> sendAtomSubmissionState.accept(atomStatus, data));

			} else if (e instanceof MempoolFullException) {
				sendAtomSubmissionState.accept(MEMPOOL_FULL, fromException(e));
			} else if (e instanceof MempoolDuplicateException) {
				sendAtomSubmissionState.accept(MEMPOOL_DUPLICATE, fromException(e));
			} else {
				sendAtomSubmissionState.accept(null, fromException(e));
			}
		}

		private JSONObject fromException(final Throwable e) {
			return jsonObject().put("message", e.getMessage());
		}

		private AtomStatus extractAtomStatus(final RadixEngineException exception, final JSONObject data) {
			switch (exception.getErrorCode()) {
				case CONVERSION_ERROR:	// Fall through
				case CM_ERROR: 			// Fall through
				case HOOK_ERROR:
					if (exception.getCmError() != null) {
						data.put("cmError", exception.getCmError().getErrMsg());
					}
					return EVICTED_FAILED_CM_VERIFICATION;

				case VIRTUAL_STATE_CONFLICT:
					return EVICTED_FAILED_CM_VERIFICATION;

				case MISSING_DEPENDENCY:
					return MISSING_DEPENDENCY;

				case STATE_CONFLICT:
					return CONFLICT_LOSER;

				default: // Don't send back unhandled exception
					return null;
			}
		}
	}
}
