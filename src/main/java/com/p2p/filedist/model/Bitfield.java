package com.p2p.filedist.model;

import java.util.BitSet;

public class Bitfield {
    private final int totalPieces;
    private final BitSet bitSet;

    public Bitfield(int totalPieces) {
        this.totalPieces = totalPieces;
        this.bitSet = new BitSet(totalPieces);
    }

    public Bitfield(int totalPieces, boolean fillAll) {
        this(totalPieces);
        if (fillAll) {
            bitSet.set(0, totalPieces);
        }
    }

    public synchronized void setPiece(int index, boolean present) {
        if (index < 0 || index >= totalPieces) {
            throw new IndexOutOfBoundsException("Piece index " + index + " out of bounds for total " + totalPieces);
        }
        bitSet.set(index, present);
    }

    public synchronized boolean hasPiece(int index) {
        if (index < 0 || index >= totalPieces) {
            return false;
        }
        return bitSet.get(index);
    }

    public synchronized boolean isComplete() {
        return bitSet.cardinality() == totalPieces;
    }

    public synchronized int getCompletedCount() {
        return bitSet.cardinality();
    }

    public int getTotalPieces() {
        return totalPieces;
    }

    public synchronized byte[] toByteArray() {
        int byteCount = (totalPieces + 7) / 8;
        byte[] bytes = new byte[byteCount];
        for (int i = 0; i < totalPieces; i++) {
            if (bitSet.get(i)) {
                bytes[i / 8] |= (byte) (1 << (7 - (i % 8)));
            }
        }
        return bytes;
    }

    public static Bitfield fromByteArray(byte[] bytes, int totalPieces) {
        Bitfield bitfield = new Bitfield(totalPieces);
        for (int i = 0; i < totalPieces; i++) {
            int byteIndex = i / 8;
            int bitOffset = 7 - (i % 8);
            if (byteIndex < bytes.length && ((bytes[byteIndex] >> bitOffset) & 1) == 1) {
                bitfield.setPiece(i, true);
            }
        }
        return bitfield;
    }

    @Override
    public synchronized String toString() {
        return "Bitfield[" + bitSet.cardinality() + "/" + totalPieces + " pieces]";
    }
}
