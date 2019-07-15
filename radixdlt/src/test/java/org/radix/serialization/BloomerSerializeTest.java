package org.radix.serialization;

import org.radix.utils.Bloomer;

/**
 * Check serialization of Bloomer
 */
@SuppressWarnings("rawtypes")
public class BloomerSerializeTest extends SerializeObject<Bloomer> {
	public BloomerSerializeTest() {
		super(Bloomer.class, BloomerSerializeTest::get);
	}

	private static Bloomer get() {
		Bloomer<String> b = new Bloomer<>(100, 50, "test");
		b.add("foo");
		b.add("bar");
		b.add("hoge");
		b.add("piyo");
		return b;
	}
}
