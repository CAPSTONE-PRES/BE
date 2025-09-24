package com.pres.pres_server.exception;

public class KakaoInternalServerException extends KakaoException {
    public KakaoInternalServerException() {
        super();
    }

    public KakaoInternalServerException(String message) {
        super(message);
    }

    public KakaoInternalServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public KakaoInternalServerException(Throwable cause) {
        super(cause);
    }
}
