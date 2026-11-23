package dev.bearwithme.common.error;

/** A requested resource does not exist (or belongs to another user). Mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
