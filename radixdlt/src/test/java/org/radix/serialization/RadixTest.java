package org.radix.serialization;

import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.serialization.Serialization;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.common.EUID;
import org.radix.atoms.AtomEngineStore;
import org.radix.atoms.AtomStore;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import org.radix.shards.ShardSpace;
import org.radix.time.NtpService;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemMetaData;
import org.radix.validation.ValidationHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.security.Security;

import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public abstract class RadixTest
{
	@BeforeClass
	public static void startRadixTest() throws Exception
	{
		Field isRestricted = Class.forName("javax.crypto.JceSecurity").getDeclaredField("isRestricted");


		if ( Modifier.isFinal(isRestricted.getModifiers()) )
		{
			Field modifiers = Field.class.getDeclaredField("modifiers");
			modifiers.setAccessible(true);
			modifiers.setInt(isRestricted, isRestricted.getModifiers() & ~Modifier.FINAL);
		}

		try {
			isRestricted.setAccessible(true);
			if (isRestricted.get(null).equals(Boolean.TRUE)) {
				System.out.print("Encryption restrictions are set, need to override...");
				isRestricted.setBoolean(null, false);
				System.out.println("...override success!");
			}
			isRestricted.setAccessible(false);
		} catch (IllegalAccessException e) {
			System.out.println("...failed.  Best of luck.");
		}

		Security.addProvider(new BouncyCastleProvider());

		final SecureRandom secureRandom = new SecureRandom();

		final RuntimeProperties runtimeProperties = mock(RuntimeProperties.class);
		doAnswer(invocation -> invocation.getArgument(1)).when(runtimeProperties).get(any(), any());

		final Universe universe = mock(Universe.class);
		when(universe.getMagic()).thenReturn(2);

		final SystemMetaData systemMetaData = mock(SystemMetaData.class);
		when(systemMetaData.getUID()).thenReturn(new EUID(2));

		final NtpService ntpService = mock(NtpService.class);
		Serialization serialization = Serialization.getDefault();

		final LocalSystem localSystem = mock(LocalSystem.class);
		when(localSystem.getShards()).thenReturn(new ShardSpace(10000, 20000));

		Modules.put(RuntimeProperties.class, runtimeProperties);
		Modules.put(Serialization.class, serialization);
		Modules.put(SecureRandom.class, secureRandom);
		Modules.put(Universe.class, universe);
		Modules.put(SystemMetaData.class, systemMetaData);
		Modules.put(NtpService.class, ntpService);
		Modules.put(LocalSystem.class, localSystem);

		CMAtomOS os = new CMAtomOS();
		os.load(new TokensConstraintScrypt());
		os.load(new UniqueParticleConstraintScrypt());
		os.load(new MessageParticleConstraintScrypt());

		final AtomEngineStore atomStore = new AtomEngineStore(
			() -> Modules.get(AtomStore.class),
			() -> LocalSystem.getInstance().getShards()
		);

		final ConstraintMachine constraintMachine = new ConstraintMachine.Builder()
			.setParticleTransitionProcedures(os.buildTransitionProcedures())
			.setParticleStaticCheck(os.buildParticleStaticCheck())
			.build();

		final RadixEngine<SimpleRadixEngineAtom> radixEngine = new RadixEngine<>(
			constraintMachine,
			os.buildVirtualLayer(),
			atomStore
		);

		Modules.put(ValidationHandler.class, new ValidationHandler(radixEngine));
	}

	@AfterClass
	public static void finishRadixTest() {
		Modules.remove(ValidationHandler.class);
		Modules.remove(RuntimeProperties.class);
		Modules.remove(Serialization.class);
		Modules.remove(SecureRandom.class);
		Modules.remove(Universe.class);
		Modules.remove(SystemMetaData.class);
		Modules.remove(NtpService.class);
		Modules.remove(LocalSystem.class);
	}

}
