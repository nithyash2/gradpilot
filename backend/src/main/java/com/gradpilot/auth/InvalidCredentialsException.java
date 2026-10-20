package com.gradpilot.auth;

/**
 * Thrown for a wrong email or a wrong password. Both cases share one message
 * so the API never reveals which emails have accounts.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email or password is incorrect");
    }
}
