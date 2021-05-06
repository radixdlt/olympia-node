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

package com.radixdlt.client.lib.api;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class NavigationCursor {
	private final String content;

	private NavigationCursor(String content) {
		this.content = content;
	}

	@JsonCreator
	public static NavigationCursor create(String content) {
		requireNonNull(content);

		return new NavigationCursor(content);
	}

	public String value() {
		return content;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		return o instanceof NavigationCursor && content.equals(((NavigationCursor) o).content);
	}

	@Override
	public int hashCode() {
		return Objects.hash(content);
	}

	@Override
	public String toString() {
		return "NavigationCursor('" + content + "')";
	}
}
