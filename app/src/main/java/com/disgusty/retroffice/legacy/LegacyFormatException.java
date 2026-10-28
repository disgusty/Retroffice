package com.disgusty.retroffice.legacy;

import java.io.IOException;

/** A legacy file we can't read; {@link #encrypted} tells the user why. */
public final class LegacyFormatException extends IOException {
    public final boolean encrypted;

    public LegacyFormatException(String message, boolean encrypted) {
        super(message);
        this.encrypted = encrypted;
    }
}
