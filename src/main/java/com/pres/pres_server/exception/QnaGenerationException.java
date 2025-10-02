package com.pres.pres_server.exception;

public class QnaGenerationException extends RuntimeException {
    public QnaGenerationException(String message) {
        super(message);
    }

    public QnaGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}