package com.radixdlt.engine;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.constraintmachine.DataPointer;
import org.junit.Test;

public class RadixEngineExceptionTest {
	@Test
	public void testGetters() {
		RadixEngineErrorCode code = mock(RadixEngineErrorCode.class);
		DataPointer dp = mock(DataPointer.class);
		RadixEngineAtom related = mock(RadixEngineAtom.class);
		RadixEngineException e = new RadixEngineException(code, dp, related);
		assertThat(e.getRelated()).isEqualTo(related);
		assertThat(e.getDataPointer()).isEqualTo(dp);
		assertThat(e.getErrorCode()).isEqualTo(code);
	}
}