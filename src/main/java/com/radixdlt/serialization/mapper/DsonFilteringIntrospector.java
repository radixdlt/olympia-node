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

package com.radixdlt.serialization.mapper;

import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

/**
 * An annotation introspector that defaults to using the DSON filter
 * if no other filter is specified.
 */
class DsonFilteringIntrospector extends JacksonAnnotationIntrospector {
	private static final long serialVersionUID = 29L;

	@Override
	public Object findFilterId(Annotated a) {
		Object id = super.findFilterId(a);
		if (id == null) {
			return MapperConstants.DSON_FILTER_NAME;
		}
		return id;
	}
}
