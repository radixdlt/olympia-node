package com.radixdlt.atomos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.Security;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

public class RRITest {
	@BeforeClass
	public static void setup() {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);
	}

	@Test
	public void when_parsing_a_correctly_formed_rri__exception_is_not_thrown() {
		List<String> correctRRIs = Arrays.asList(
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/name",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/name-",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/-name",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/NAME",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/name123456",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/1"
		);

		correctRRIs.forEach(rriStr -> assertThat(RRI.from(rriStr)).isNotNull());
	}

	@Test
	public void when_parsing_bad_structure__illegal_argument_exception_should_occur() {
		List<String> badTypeRRIs = Arrays.asList(
			"a/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/type/name",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/type/",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor//"
		);

		badTypeRRIs.forEach(rriStr ->
			assertThatThrownBy(() -> RRI.from(rriStr))
				.isInstanceOf(IllegalArgumentException.class)
		);
	}


	@Test
	public void when_parsing_bad_type__illegal_argument_exception_should_occur() {
		List<String> badTypeRRIs = Arrays.asList(
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor//name",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/ /name",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/ type/NAME",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/_type/name123456",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/:e/1",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor//1"
		);

		badTypeRRIs.forEach(rriStr ->
			assertThatThrownBy(() -> RRI.from(rriStr))
				.isInstanceOf(IllegalArgumentException.class)
		);
	}

	@Test
	public void when_parsing_bad_name__illegal_argument_exception_should_occur() {
		List<String> badTypeRRIs = Arrays.asList(
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/type/#",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/type/a b",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/type/*",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/type/ name",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/type/ ",
			"/JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor/type/"
		);

		badTypeRRIs.forEach(rriStr ->
			assertThatThrownBy(() -> RRI.from(rriStr))
				.isInstanceOf(IllegalArgumentException.class)
		);
	}
}