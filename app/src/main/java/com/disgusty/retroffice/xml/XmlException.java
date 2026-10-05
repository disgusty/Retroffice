package com.disgusty.retroffice.xml;

public final class XmlException extends Exception {
    public final int offset;

    public XmlException(String message, int offset) {
        super(message + " (at " + offset + ")");
        this.offset = offset;
    }
}
