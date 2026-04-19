package dev.knalis.trajectaapi.exception;

public class InternalServerException extends RuntimeException {
    public InternalServerException(String message) {
        super(message);
    }
    
    public InternalServerException(String message, Exception e) {
        super(message, e);
    }
}


