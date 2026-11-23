package dev.bearwithme.common.error;

/** Input that Bean Validation cannot express, e.g. a CSV file with a wrong header. Mapped to 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
