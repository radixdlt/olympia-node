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

package com.radixdlt.constraintmachine;

import com.radixdlt.store.ReadableAddrs;

/**
 * Validates whether a specific transition procedure is permissible
 */
public final class Authorization {
	public interface Authorizer {
		void verify(ReadableAddrs readableAddrs, ExecutionContext context) throws AuthorizationException;
	}
	private final PermissionLevel permissionLevel;
	private final Authorizer authorizer;

	public Authorization(PermissionLevel permissionLevel, Authorizer authorizer) {
		this.permissionLevel = permissionLevel;
		this.authorizer = authorizer;
	}

	public PermissionLevel permissionLevel() {
		return permissionLevel;
	}

	public Authorizer authorizer() {
		return authorizer;
	}
}
