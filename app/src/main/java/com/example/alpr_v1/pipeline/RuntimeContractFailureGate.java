package com.example.alpr_v1.pipeline;

final class RuntimeContractFailureGate {
    private String message;

    synchronized boolean record(String value) {
        if (message != null) return false;
        message = value == null || value.trim().isEmpty()
                ? "Model nie odpowiada manifestowi"
                : value;
        return true;
    }

    synchronized boolean isBlocked() {
        return message != null;
    }

    synchronized String message() {
        return message;
    }

    synchronized void clear() {
        message = null;
    }
}
