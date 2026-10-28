package com.disgusty.retroffice.legacy;

import java.io.File;
import java.io.IOException;

import com.disgusty.retroffice.doc.FileUtil;

/**
 * Turns a legacy document (.doc, .xls, .ppt, .rtf) into an equivalent OpenDocument file that the
 * normal engine can open and edit. The original file is only read, never changed.
 */
public final class LegacyConverter {
    private LegacyConverter() {
    }

    /** What was converted: the new ODF file, its extension and the source format's name. */
    public static final class Result {
        public final File file;
        public final String extension, sourceFormat;

        Result(File file, String extension, String sourceFormat) {
            this.file = file;
            this.extension = extension;
            this.sourceFormat = sourceFormat;
        }
    }

    public static boolean isLegacy(byte[] head) {
        return Cfb.isCfb(head) || RtfReader.looksLikeRtf(head);
    }

    /**
     * @return the converted file, or null if the OLE container holds no document we know
     * @throws LegacyFormatException for encrypted files
     */
    public static Result convert(File src, File outDir) throws IOException {
        byte[] data = FileUtil.readAll(src, 128L * 1024 * 1024);
        if (RtfReader.looksLikeRtf(data)) {
            File out = new File(outDir, "converted.odt");
            OdfBuilder.writeText(RtfReader.read(data), out);
            return new Result(out, "odt", "RTF");
        }
        Cfb cfb = new Cfb(data);
        data = null; // the container keeps its own copy of what it needs
        if (cfb.has("EncryptionInfo") || cfb.has("EncryptedPackage")) {
            throw new LegacyFormatException("Password-protected document", true);
        }
        if (cfb.has("WordDocument")) {
            File out = new File(outDir, "converted.odt");
            OdfBuilder.writeText(DocReader.read(cfb), out);
            return new Result(out, "odt", "DOC");
        }
        if (cfb.has("Workbook") || cfb.has("Book")) {
            File out = new File(outDir, "converted.ods");
            OdfBuilder.writeSheets(XlsReader.read(cfb), out);
            return new Result(out, "ods", "XLS");
        }
        if (cfb.has("PowerPoint Document")) {
            File out = new File(outDir, "converted.odp");
            OdfBuilder.writeSlides(PptReader.read(cfb), out);
            return new Result(out, "odp", "PPT");
        }
        return null;
    }
}
