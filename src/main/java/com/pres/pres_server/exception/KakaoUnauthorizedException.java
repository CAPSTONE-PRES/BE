package com.pres.pres_server.exception;

public class KakaoUnauthorizedException extends KakaoException {
    public KakaoUnauthorizedException() {
        super();
    }

    public KakaoUnauthorizedException(String message) {
        super(message);
    }

    public KakaoUnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

    public KakaoUnauthorizedException(Throwable cause) {
        super(cause);
    }
}
