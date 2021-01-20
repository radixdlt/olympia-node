package com.radixdlt.integration.distributed.simulation;

import com.google.inject.multibindings.MapKey;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@MapKey(unwrapValue = true)
@Retention(RetentionPolicy.RUNTIME)
public @interface MonitorKey {
    Monitor value();
}
