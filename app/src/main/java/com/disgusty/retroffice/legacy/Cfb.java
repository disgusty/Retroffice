package com.disgusty.retroffice.legacy;

import java.io.IOException;
import java.util.HashMap;

/**
 * Reader for the OLE2 "Compound File Binary" container used by .doc, .xls and .ppt.
 *
 * The file works like a tiny disk: fixed-size sectors, a FAT (sector chain table, as on a
 * memory stick) and a directory of named streams. Small streams live in a separate
 * "mini stream" with 64-byte sectors. Every chain walk is bounded, so a damaged file throws
 * instead of looping forever.
 */
public final class Cfb {
    private static final int FREE = -1, END = -2;

    private final byte[] d;
    private final int sectorSize, miniSectorSize, miniCutoff;
    private final int[] fat;
    private int[] miniFat = new int[0];
    private byte[] miniStream = new byte[0];
    private final HashMap<String, int[]> streams = new HashMap<String, int[]>(); // name -> {start, size}

    public static boolean isCfb(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF && (b[2] & 0xFF) == 0x11
                && (b[3] & 0xFF) == 0xE0 && (b[4] & 0xFF) == 0xA1 && (b[5] & 0xFF) == 0xB1
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0xE1;
    }

    public Cfb(byte[] data) throws IOException {
        d = data;
        if (!isCfb(d) || d.length < 512) throw new IOException("Not an OLE2 file");
        int shift = u16(0x1E);
        if (shift != 9 && shift != 12) throw new IOException("Bad sector size");
        sectorSize = 1 << shift;
        miniSectorSize = 1 << u16(0x20);
        miniCutoff = s32(0x38);
        int numFat = s32(0x2C), firstDir = s32(0x30), firstMiniFat = s32(0x3C), numMiniFat = s32(0x40);
        int firstDifat = s32(0x44), numDifat = s32(0x48);
        if (numFat < 0 || numFat > d.length / sectorSize + 1) throw new IOException("Damaged OLE2 header");

        // FAT sector numbers: 109 in the header, the rest in a chain of DIFAT sectors.
        int[] fatSectors = new int[numFat];
        int n = 0;
        for (int i = 0; i < 109 && n < numFat; i++) fatSectors[n++] = s32(0x4C + i * 4);
        int difat = firstDifat, per = sectorSize / 4 - 1;
        for (int k = 0; k < numDifat && n < numFat && difat >= 0; k++) {
            int off = offset(difat);
            for (int i = 0; i < per && n < numFat; i++) fatSectors[n++] = s32(off + i * 4);
            difat = s32(off + per * 4);
        }
        int perSector = sectorSize / 4;
        fat = new int[numFat * perSector];
        for (int i = 0; i < numFat; i++) {
            int off = offset(fatSectors[i]);
            for (int j = 0; j < perSector; j++) fat[i * perSector + j] = s32(off + j * 4);
        }

        // Directory: 128-byte entries in a regular chain.
        byte[] dir = chain(firstDir, -1, fat, sectorSize, null);
        int rootStart = END, rootSize = 0;
        for (int e = 0; e + 128 <= dir.length; e += 128) {
            int type = dir[e + 66] & 0xFF;
            if (type == 0) continue;
            int nameLen = (dir[e + 64] & 0xFF) | ((dir[e + 65] & 0xFF) << 8);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i + 1 < Math.min(nameLen, 64) - 1; i += 2) {
                sb.append((char) ((dir[e + i] & 0xFF) | ((dir[e + i + 1] & 0xFF) << 8)));
            }
            int start = le32(dir, e + 116), size = le32(dir, e + 120);
            if (type == 5) {
                rootStart = start;
                rootSize = size;
            } else if (type == 2 && !streams.containsKey(sb.toString())) {
                streams.put(sb.toString(), new int[]{start, size});
            }
        }
        if (numMiniFat > 0 && firstMiniFat >= 0) {
            byte[] mf = chain(firstMiniFat, -1, fat, sectorSize, null);
            miniFat = new int[mf.length / 4];
            for (int i = 0; i < miniFat.length; i++) miniFat[i] = le32(mf, i * 4);
        }
        if (rootStart >= 0) miniStream = chain(rootStart, rootSize, fat, sectorSize, null);
    }

    public boolean has(String name) {
        return streams.containsKey(name);
    }

    /** Contents of a top-level stream, or null if it doesn't exist. */
    public byte[] stream(String name) throws IOException {
        int[] s = streams.get(name);
        if (s == null) return null;
        if (s[1] < miniCutoff) return chain(s[0], s[1], miniFat, miniSectorSize, miniStream);
        return chain(s[0], s[1], fat, sectorSize, null);
    }

    /** Follows a sector chain; {@code size} < 0 means "until the end of the chain". */
    private byte[] chain(int start, int size, int[] table, int secSize, byte[] mini) throws IOException {
        if (size == 0 || start < 0) return new byte[0];
        int max = table.length + 1;
        int cap = size >= 0 ? size : max * secSize;
        if (size > d.length * 2L + 1024 * 1024) throw new IOException("Damaged OLE2 stream size");
        byte[] out = new byte[Math.min(cap, (mini != null ? mini.length : d.length) + secSize)];
        int pos = 0, sec = start, steps = 0;
        while (sec >= 0 && pos < out.length) {
            if (++steps > max || sec >= table.length) throw new IOException("Damaged OLE2 chain");
            int off;
            byte[] src;
            if (mini != null) {
                off = sec * secSize;
                src = mini;
            } else {
                off = offset(sec);
                src = d;
            }
            int len = Math.min(secSize, out.length - pos);
            if (off + len > src.length) len = Math.max(0, src.length - off);
            if (len <= 0) break;
            System.arraycopy(src, off, out, pos, len);
            pos += len;
            sec = table[sec];
        }
        if (size >= 0 && pos < size) throw new IOException("Truncated OLE2 stream");
        if (pos == out.length) return out;
        byte[] r = new byte[pos];
        System.arraycopy(out, 0, r, 0, pos);
        return r;
    }

    private int offset(int sector) throws IOException {
        long off = (long) (sector + 1) * sectorSize;
        if (sector < 0 || off + sectorSize > d.length + sectorSize) throw new IOException("Damaged OLE2 sector");
        return (int) off;
    }

    private int u16(int off) {
        return (d[off] & 0xFF) | ((d[off + 1] & 0xFF) << 8);
    }

    private int s32(int off) {
        return le32(d, off);
    }

    static int le32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    static int le16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }
}
