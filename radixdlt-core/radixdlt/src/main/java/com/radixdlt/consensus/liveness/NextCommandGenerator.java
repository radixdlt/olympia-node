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

package com.radixdlt.consensus.liveness;

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.View;

import java.util.List;

/**
 * Generates a new proposed command for a given view
 */
public interface NextCommandGenerator {

	/**
	 * Generates a valid command for the given view
	 * TODO: Update interface to return an error if already generated a command for a given view
	 * @param view the view to create the vertex for
	 * @param prepared vertices with commands which have already been prepared
	 * @return new command to execute next
	 */
	Command generateNextCommand(View view, List<PreparedVertex> prepared);
}
