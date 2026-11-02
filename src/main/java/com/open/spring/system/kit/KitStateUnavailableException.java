package com.open.spring.system.kit;

/** A kit or feature change was refused because the stored admin choices cannot be read right now. */
public class KitStateUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public KitStateUnavailableException(String message) {
        super(message);
    }
}
