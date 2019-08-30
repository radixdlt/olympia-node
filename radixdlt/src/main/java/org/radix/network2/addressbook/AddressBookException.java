package org.radix.network2.addressbook;

public class AddressBookException extends RuntimeException {

	public AddressBookException(String msg) {
		super(msg);
	}

	public AddressBookException(String msg, Exception cause) {
		super(msg, cause);
	}

}
