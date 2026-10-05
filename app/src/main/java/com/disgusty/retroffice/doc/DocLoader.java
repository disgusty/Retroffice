package com.disgusty.retroffice.doc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import com.disgusty.retroffice.pkg.OfficePackage;
import com.disgusty.retroffice.sheet.CsvDoc;
import com.disgusty.retroffice.sheet.OdsDoc;
import com.disgusty.retroffice.sheet.XlsxDoc;
import com.disgusty.retroffice.slides.SlidesDoc;
import com.disgusty.retroffice.xml.XDoc;
import com.disgusty.retroffice.xml.XmlException;

/** Opens a local copy of a document, choosing the format by content (not only by name). */
public final class DocLoader {
    private DocLoader() {
    }

    public static final class OpenException extends Exception {
        public static final int UNSUPPORTED = 1, PROTECTED = 2, LEGACY = 3, DAMAGED = 4, TOO_LARGE = 5;
        public final int reason;

        public OpenException(int reason, String detail) {
            super(detail);
            this.reason = reason;
        }
    }

    public static Doc open(File f, String displayName, Images images) throws OpenException {
        String ext = FileUtil.extension(displayName);
        byte[] head = new byte[8];
        int n = 0;
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                n = in.read(head);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new OpenException(OpenException.DAMAGED, e.getMessage());
        }
        try {
            Doc d;
            if (n >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4) {
                d = openPackage(f, images);
            } else if (n >= 8 && (head[0] & 0xFF) == 0xD0 && (head[1] & 0xFF) == 0xCF && (head[2] & 0xFF) == 0x11 && (head[3] & 0xFF) == 0xE0) {
                // OLE container: legacy .doc/.xls/.ppt, or an encrypted OOXML file.
                if (ext.equals("docx") || ext.equals("xlsx") || ext.equals("pptx")) {
                    throw new OpenException(OpenException.PROTECTED, "Password-protected document");
                }
                throw new OpenException(OpenException.LEGACY, "Legacy binary format");
            } else if (n >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
                throw new OpenException(OpenException.UNSUPPORTED, "PDF");
            } else if (ext.equals("csv") || ext.equals("tsv") || ext.equals("tab")) {
                d = CsvDoc.open(f, ext);
            } else if (isZipName(ext)) {
                throw new OpenException(OpenException.DAMAGED, "Not a valid " + ext.toUpperCase(java.util.Locale.US) + " file");
            } else {
                d = PlainDoc.open(f, ext.length() == 0 ? "txt" : ext);
            }
            d.displayName = displayName;
            return d;
        } catch (OpenException e) {
            throw e;
        } catch (OutOfMemoryError e) {
            throw new OpenException(OpenException.TOO_LARGE, "Not enough memory");
        } catch (XmlException e) {
            throw new OpenException(OpenException.DAMAGED, e.getMessage());
        } catch (IOException e) {
            String m = e.getMessage();
            if (m != null && m.contains("too large")) throw new OpenException(OpenException.TOO_LARGE, m);
            throw new OpenException(OpenException.DAMAGED, m);
        } catch (Exception e) {
            throw new OpenException(OpenException.DAMAGED, String.valueOf(e.getMessage()));
        }
    }

    private static boolean isZipName(String ext) {
        return ext.equals("docx") || ext.equals("xlsx") || ext.equals("pptx") || ext.equals("odt")
                || ext.equals("ods") || ext.equals("odp");
    }

    private static Doc openPackage(File f, Images images) throws Exception {
        OfficePackage pkg = OfficePackage.open(f);
        boolean ok = false;
        try {
            Doc d;
            if (pkg.has("mimetype") || pkg.has("content.xml")) {
                d = openOdf(pkg, images);
            } else if (pkg.has("[Content_Types].xml")) {
                d = openOoxml(pkg, images);
            } else {
                throw new OpenException(OpenException.UNSUPPORTED, "Unknown package");
            }
            ok = true;
            return d;
        } finally {
            if (!ok) pkg.close();
        }
    }

    private static Doc openOdf(OfficePackage pkg, Images images) throws Exception {
        String mime = "";
        byte[] mt = pkg.readOrNull("mimetype");
        if (mt != null) mime = new String(mt, "US-ASCII").trim();
        if (isEncryptedOdf(pkg)) throw new OpenException(OpenException.PROTECTED, "Password-protected document");
        if (mime.startsWith("application/vnd.oasis.opendocument.text")) return TextDoc.openOdt(pkg, images);
        if (mime.startsWith("application/vnd.oasis.opendocument.spreadsheet")) return OdsDoc.open(pkg);
        if (mime.startsWith("application/vnd.oasis.opendocument.presentation")) return SlidesDoc.openOdp(pkg, images);
        // No (or unusual) mimetype: look at the body.
        XDoc c = pkg.xml("content.xml");
        com.disgusty.retroffice.xml.XNode body = c.root.child(com.disgusty.retroffice.xml.Xml.OFFICE, "body");
        if (body != null) {
            String O = com.disgusty.retroffice.xml.Xml.OFFICE;
            if (body.child(O, "text") != null) return TextDoc.openOdt(pkg, images);
            if (body.child(O, "spreadsheet") != null) return OdsDoc.open(pkg);
            if (body.child(O, "presentation") != null) return SlidesDoc.openOdp(pkg, images);
        }
        throw new OpenException(OpenException.UNSUPPORTED, "Unsupported OpenDocument type " + mime);
    }

    private static boolean isEncryptedOdf(OfficePackage pkg) {
        try {
            XDoc m = pkg.xmlOrNull("META-INF/manifest.xml");
            return m != null && m.root.find(com.disgusty.retroffice.xml.Xml.MANIFEST, "encryption-data") != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static Doc openOoxml(OfficePackage pkg, Images images) throws Exception {
        String main = Rels.byType(pkg, "", "/officeDocument");
        if (main == null || !pkg.has(main)) throw new OpenException(OpenException.DAMAGED, "Main document part missing");
        String low = main.toLowerCase(java.util.Locale.US);
        if (low.startsWith("word/") || low.endsWith("document.xml")) return TextDoc.openDocx(pkg, main, images);
        if (low.startsWith("xl/") || low.endsWith("workbook.xml")) return XlsxDoc.open(pkg, main);
        if (low.startsWith("ppt/") || low.endsWith("presentation.xml")) return SlidesDoc.openPptx(pkg, main, images);
        throw new OpenException(OpenException.UNSUPPORTED, "Unknown Office document");
    }
}
