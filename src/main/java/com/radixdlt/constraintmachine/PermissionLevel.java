package com.radixdlt.constraintmachine;

/**
 * Ordered Execution level which specifies what level of permission the
 * constraint machine is running on. Depending on this level, some particle
 * transitions may be allowed or not.
 */
public enum PermissionLevel {
	USER,
	SYSTEM
}
