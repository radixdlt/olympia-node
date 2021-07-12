/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.application.system.construction;

import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.identifiers.REAddr;

import java.nio.charset.StandardCharsets;

public class CreateSystemConstructorV2 implements ActionConstructor<CreateSystem> {
	@Override
	public void construct(CreateSystem action, TxBuilder builder) throws TxBuilderException {
		builder.up(new VirtualParent(new byte[] {SubstateTypeId.UNCLAIMED_READDR.id()}));
		builder.end();


		builder.toLowLevelBuilder().syscall(Syscall.READDR_CLAIM, "sys".getBytes(StandardCharsets.UTF_8));
		builder.downREAddr(REAddr.ofSystem());
		builder.up(new EpochData(0));
		builder.up(new RoundData(0, action.getTimestamp()));
		builder.up(new VirtualParent(new byte[] {SubstateTypeId.VALIDATOR_META_DATA.id()}));
		builder.up(new VirtualParent(new byte[] {SubstateTypeId.VALIDATOR_STAKE_DATA.id()}));
		builder.up(new VirtualParent(new byte[] {SubstateTypeId.VALIDATOR_ALLOW_DELEGATION_FLAG.id()}));
		builder.up(new VirtualParent(new byte[] {SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY.id()}));
		builder.up(new VirtualParent(new byte[] {SubstateTypeId.VALIDATOR_RAKE_COPY.id()}));
		builder.up(new VirtualParent(new byte[] {SubstateTypeId.VALIDATOR_OWNER_COPY.id()}));
		builder.end();
	}
}
