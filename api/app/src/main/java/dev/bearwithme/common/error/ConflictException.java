package dev.bearwithme.common.error;

/** The request conflicts with existing data, for example a duplicate term (FR-C2). Mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
