package org.radix.api.services;

public class MessagesService {
	private final static MessagesService MESSAGES_SERVICE = new MessagesService();

	public static MessagesService getInstance() {
		return MESSAGES_SERVICE;
	}

	private MessagesService() {}
}
