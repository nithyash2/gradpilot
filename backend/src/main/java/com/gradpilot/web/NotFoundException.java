package com.gradpilot.web;

/**
 * Something does not exist, or belongs to another user. Both cases return
 * 404 so the API never confirms that another person's data exists.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what) {
        super(what + " not found");
    }
}
