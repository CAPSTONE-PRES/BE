package com.pres.pres_server.exception;

public class KakaoBadRequestException extends KakaoException {
    public KakaoBadRequestException() {
        super();
    }

    public KakaoBadRequestException(String message) {
        super(message);
    }

    public KakaoBadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public KakaoBadRequestException(Throwable cause) {
        super(cause);
    }
}
