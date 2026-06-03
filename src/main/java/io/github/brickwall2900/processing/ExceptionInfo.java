package io.github.brickwall2900.processing;

import java.util.UUID;

public record ExceptionInfo(UUID source, String type, String message, String[] stacktrace, ExceptionInfo cause) {
}
