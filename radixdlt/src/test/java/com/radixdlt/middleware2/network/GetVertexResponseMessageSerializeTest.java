/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.network;


import com.radixdlt.consensus.Vertex;
import org.radix.serialization.SerializeMessageObject;

public class GetVertexResponseMessageSerializeTest  extends SerializeMessageObject<GetVertexResponseMessage> {
	public GetVertexResponseMessageSerializeTest() {
		super(GetVertexResponseMessage.class, GetVertexResponseMessageSerializeTest::get);
	}

	private static GetVertexResponseMessage get() {
		return new GetVertexResponseMessage(1234, Vertex.createGenesis(null));
	}

}
