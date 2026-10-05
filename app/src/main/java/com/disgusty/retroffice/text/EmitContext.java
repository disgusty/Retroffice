package com.disgusty.retroffice.text;

import java.util.HashMap;
import java.util.LinkedHashMap;

/** Side outputs of writing paragraphs, e.g. ODF automatic styles that must be declared. */
public final class EmitContext {
    /** New ODF automatic text styles: name -> full style element markup. */
    public final LinkedHashMap<String, String> newStyles = new LinkedHashMap<String, String>();
    /** Lookup from a formatting key to an already generated style name. */
    public final HashMap<String, String> styleByKey = new HashMap<String, String>();
    /** Generated wrapper frames, shared so consecutive runs with the same override merge. */
    public final HashMap<String, Object> frameCache = new HashMap<String, Object>();
}
