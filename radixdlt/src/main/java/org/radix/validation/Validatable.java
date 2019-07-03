package org.radix.validation;

import org.radix.state.DomainedState;

public interface Validatable extends DomainedState
{
	public boolean isValidated(Validator.Mode mode);
	public void setValidated(Validator.Mode mode);
}