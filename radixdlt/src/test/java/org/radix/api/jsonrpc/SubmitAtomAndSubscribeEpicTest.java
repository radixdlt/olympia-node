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

import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import org.everit.json.schema.Schema;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.InOrder;
import org.radix.api.services.AtomsService;
import org.radix.api.services.SingleAtomListener;
import com.radixdlt.constraintmachine.DataPointer;
import org.radix.validation.ConstraintMachineValidationException;
import com.radixdlt.serialization.Serialization;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class SubmitAtomAndSubscribeEpicTest {
	interface ConsumerJSONObject extends Consumer<JSONObject> {
		// Nothing here
	}

	@Test
	public void testAtomValidationError() {
		AtomsService atomsService = mock(AtomsService.class);
		Schema schema = mock(Schema.class);
		Serialization serializer = mock(Serialization.class);
		Consumer<JSONObject> callback = mock(ConsumerJSONObject.class);
		JSONObject action = mock(JSONObject.class);
		JSONObject params = mock(JSONObject.class);
		JSONObject jsonAtom = new JSONObject();
		when(action.getJSONObject("params")).thenReturn(params);
		when(params.getJSONObject("atom")).thenReturn(jsonAtom);

		Atom atom = mock(Atom.class);

		when(serializer.fromJsonObject(eq(jsonAtom), eq(Atom.class))).thenReturn(atom);

		doAnswer((invocation) -> {
			((SingleAtomListener) invocation.getArguments()[1]).onError(AID.ZERO, new ConstraintMachineValidationException(atom, "", DataPointer.ofAtom()));
			return null;
		}).when(atomsService).submitAtom(any(), any());

		SubmitAtomAndSubscribeEpic epic = new SubmitAtomAndSubscribeEpic(atomsService, schema, callback);
		epic.action(action);

		InOrder inOrder = inOrder(callback);
		inOrder.verify(callback, times(1)).accept(argThat(o ->
			o.has("method") && o.has("params")
				&& o.getString("method").equals("AtomSubmissionState.onNext")
				&& o.getJSONObject("params").getJSONObject("data").has("pointerToIssue")
		));
	}
}