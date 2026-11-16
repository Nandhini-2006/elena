package com.p2p.filedist.model;

public class PieceInfo {
    private final int index;
    private final long offset;
    private final int length;
    private final String expectedSha256;

    public PieceInfo(int index, long offset, int length, String expectedSha256) {
        this.index = index;
        this.offset = offset;
        this.length = length;
        this.expectedSha256 = expectedSha256;
    }

    public int getIndex() {
        return index;
    }

    public long getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public String getExpectedSha256() {
        return expectedSha256;
    }

    @Override
    public String toString() {
        return "PieceInfo{index=" + index + ", offset=" + offset + ", length=" + length + ", sha256=" + expectedSha256 + "}";
    }
}
