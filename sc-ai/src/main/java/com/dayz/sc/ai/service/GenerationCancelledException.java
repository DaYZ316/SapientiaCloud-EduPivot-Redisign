package com.dayz.sc.ai.service;

/**
 * Raised when an AI generation task has been cancelled by the user.
 */
public class GenerationCancelledException extends RuntimeException {

    public GenerationCancelledException(String requestId) {
        super("Generation task cancelled: " + requestId);
    }
}
