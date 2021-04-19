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
package org.radix.api.jsonrpc.handler;

import org.junit.Test;
import org.radix.api.services.AtomsService;

import com.radixdlt.identifiers.AID;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public class AtomHandlerTest {
	private static final AtomsService atomsService = mock(AtomsService.class);
	private static final AtomHandler atomHandler = new AtomHandler(atomsService);

	@Test
	public void testHandleGetAtom() {
		var jsonAtom = jsonObject().put("aid", AID.ZERO.toString());
		var request = jsonObject().put("id", 124).put("params", jsonAtom);
		when(atomsService.getAtomByAtomId(any())).thenReturn(Optional.of(jsonObject().put("content", "abc")));

		var response = atomHandler.handleGetAtom(request);

		assertEquals("{\"content\":\"abc\"}", response.toString());
	}
}