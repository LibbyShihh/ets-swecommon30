package org.opengis.cite.swecommon30;

/**
 * An enumerated type defining all recognized test run arguments.
 */
public enum TestRunArg {

    /**
     * An absolute URI that refers to a representation of the test subject or
     * metadata about it.
     */
    IUT,

    /**
     * An absolute URI that refers to the descriptor of the encoded values under
     * test.
     */
    DESCRIPTOR;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
