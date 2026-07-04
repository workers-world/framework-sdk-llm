package com.books.framework.llm.image;

public class WorkerGatewayImageException extends RuntimeException {

    public WorkerGatewayImageException(String message) {
        super(message);
    }

    public WorkerGatewayImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
