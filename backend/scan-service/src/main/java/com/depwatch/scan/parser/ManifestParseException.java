package com.depwatch.scan.parser;

public class ManifestParseException extends RuntimeException {
    public ManifestParseException(String message) {
        super(message);
    }

    public ManifestParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
