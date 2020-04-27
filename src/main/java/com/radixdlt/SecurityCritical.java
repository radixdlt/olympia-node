package com.radixdlt;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotation to be used for classes which require additional scrutiny
 * in code review. These classes will be mostly low level classes whose
 * correctness is critical for security.
 */
@Target(ElementType.TYPE)
public @interface SecurityCritical {

}
