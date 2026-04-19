package dev.knalis.trajectaapi.exception;

public final class ApiErrorCodes {

    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String FIELD_ALREADY_EXISTS = "FIELD_ALREADY_EXISTS";
    public static final String BAD_CREDENTIALS = "BAD_CREDENTIALS";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String RATE_LIMIT = "RATE_LIMIT";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String MESSAGE_NOT_READABLE = "MESSAGE_NOT_READABLE";
    public static final String ARGUMENT_TYPE_MISMATCH = "ARGUMENT_TYPE_MISMATCH";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String DEPENDENCY_UNAVAILABLE = "DEPENDENCY_UNAVAILABLE";
    public static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    public static final String UNEXPECTED_ERROR = "UNEXPECTED_ERROR";
    public static final String USER_BANNED = "USER_BANNED";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

    private ApiErrorCodes() {
    }
}


