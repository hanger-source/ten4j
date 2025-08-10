package com.tenframework.core.server;

public class GraphStoppedException extends RuntimeException {
    public GraphStoppedException(String message) {
        super(message);
    }
}