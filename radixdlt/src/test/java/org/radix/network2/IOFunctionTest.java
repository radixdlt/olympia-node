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

package org.radix.network2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

import org.junit.Test;
import org.radix.network2.IOFunction;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

public class IOFunctionTest {

	@Test
	public void testApply() throws IOException {
		IOFunction<Integer, Integer> f = t -> t == null ? 1 : t + 1;
		assertThat(f.apply(null), equalTo(1));
		assertThat(f.apply(1), equalTo(2));
	}

	@Test
	public void testCompose() throws IOException {
		IOFunction<Integer, Integer> divTwo = x -> x / 2;
		IOFunction<Integer, Integer> identity = divTwo.compose(x -> x * 2);
		assertThat(identity.apply(8), equalTo(8));

		IOFunction<Integer, Integer> identity2 = divTwo.composeFunction(x -> x * 2);
		assertThat(identity2.apply(1), equalTo(1));
	}

	@Test
	public void testAndThen() throws IOException {
		IOFunction<Integer, Integer> f1 = i -> i + 1;
		IOFunction<Integer, Integer> f2 = i -> i * 2;
		IOFunction<Integer, Integer> f = f1.andThen(f2);
		assertThat(f.apply(1), equalTo(4));

		IOFunction<Integer, Integer> g1 = i -> i + 1;
		Function<Integer, Integer> g2 = i -> i * 2;
		IOFunction<Integer, Integer> g = g1.andThenFunction(g2);
		assertThat(g.apply(1), equalTo(4));
	}

	@Test
	public void testIdentity() throws IOException {
		assertThat(IOFunction.identity().apply(null), nullValue());
		assertThat(IOFunction.identity().apply(1), equalTo(1));
	}

	@Test(expected = IOException.class)
	public void testCheckedThrows() throws IOException {
		IOFunction<Object, Object> f = t -> throwIOException();
		f.apply(null);
		fail();
	}

	@Test(expected = UncheckedIOException.class)
	public void testUncheckedThrows() {
		IOFunction<Object, Object> f = t -> throwIOException();
		IOFunction.unchecked(f).apply(null);
		fail();
	}

	@Test
	public void testUnchecked() {
		IOFunction<Integer, Integer> f = t -> t + 1;
		Function<Integer, Integer> g = IOFunction.unchecked(f);
		assertThat(g.apply(0), equalTo(1));
		assertThat(g.apply(1), equalTo(2));
	}

	public Object throwIOException() throws IOException {
		throw new IOException();
	}
}
