package com.radix.test.network.client;

public class HttpException extends RuntimeException {

    public HttpException(String message) {
        super(message);
    }

    public HttpException(Exception e) {
        super(e);
    }

    public HttpException(int code, String url) {
        super("Request to " + url + " failed: " + code);
    }
}
