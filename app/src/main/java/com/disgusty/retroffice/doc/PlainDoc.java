package com.disgusty.retroffice.doc;

import android.text.SpannableStringBuilder;

import java.io.File;
import java.io.IOException;

/** A plain-text file, saved back in its original encoding and line-ending style when possible. */
public final class PlainDoc extends Doc {
    public final SpannableStringBuilder text;
    private final TextCodec codec;
    private final String ext;
    /** Set after a save that had to switch to UTF-8. */
    public boolean switchedToUtf8;
    private TextCodec saveCodec;

    private PlainDoc(String text, TextCodec codec, String ext) {
        this.text = new SpannableStringBuilder(text);
        this.codec = codec;
        this.saveCodec = codec;
        this.ext = ext;
    }

    public static PlainDoc open(File f, String ext) throws IOException {
        byte[] b = FileUtil.readAll(f, 32L * 1024 * 1024);
        if (TextCodec.looksBinary(b)) throw new IOException("Not a text document");
        TextCodec.Decoded d = TextCodec.decode(b);
        PlainDoc doc = new PlainDoc(d.text, d.codec, ext);
        doc.markLoaded();
        return doc;
    }

    @Override
    public int kind() {
        return PLAIN;
    }

    @Override
    public String formatName() {
        return ext.length() == 0 ? "TXT" : ext.toUpperCase(java.util.Locale.US);
    }

    @Override
    public String fingerprint() {
        return text.toString();
    }

    @Override
    public String plainText() {
        return text.toString();
    }

    public String charsetName() {
        return saveCodec.charset;
    }

    @Override
    public SaveJob prepareSave() {
        final String content = text.toString();
        TextCodec c = codec;
        if (!c.canEncode(content)) {
            c = c.asUtf8();
            switchedToUtf8 = true;
        }
        saveCodec = c;
        final TextCodec use = c;
        return new SaveJob() {
            @Override
            public String fingerprint() {
                return content;
            }

            @Override
            public void write(File out) throws Exception {
                FileUtil.writeAll(out, use.encode(content));
            }

            @Override
            public void verify(File written) throws Exception {
                byte[] expected = use.encode(content);
                byte[] actual = FileUtil.readAll(written, Long.MAX_VALUE);
                if (!FileUtil.equal(expected, actual)) throw new IOException("Verification failed: text reads back differently");
            }
        };
    }

    @Override
    public void close() {
    }
}
