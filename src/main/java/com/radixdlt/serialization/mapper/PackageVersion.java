package com.radixdlt.serialization.mapper;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.Versioned;
import com.fasterxml.jackson.core.util.VersionUtil;

public final class PackageVersion implements Versioned {
	public static final Version VERSION =
		VersionUtil.parseVersion("2.9.9", "com.radixdlt.serialization.mapper", "jackson-dataformat-cbor-radix");

	@Override
	public Version version() {
		return VERSION;
	}
}
