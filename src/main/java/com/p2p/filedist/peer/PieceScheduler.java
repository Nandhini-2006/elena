package com.p2p.filedist.peer;

import com.p2p.filedist.model.Bitfield;
import com.p2p.filedist.model.FileMetadata;
import com.p2p.filedist.model.PieceStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PieceScheduler {
    private final FileMetadata metadata;
    private final Map<Integer, PieceStatus> statusMap = new ConcurrentHashMap<>();
    private final Map<Integer, Long> requestTimestamps = new ConcurrentHashMap<>();

    public PieceScheduler(FileMetadata metadata, Bitfield initialBitfield) {
        this.metadata = metadata;
        for (int i = 0; i < metadata.getTotalPieces(); i++) {
            if (initialBitfield != null && initialBitfield.hasPiece(i)) {
                statusMap.put(i, PieceStatus.VERIFIED);
            } else {
                statusMap.put(i, PieceStatus.MISSING);
            }
        }
    }

    public synchronized Integer selectNextPiece(Bitfield peerBitfield) {
        long now = System.currentTimeMillis();

        // 1. Try to find any MISSING piece that the peer has
        for (int i = 0; i < metadata.getTotalPieces(); i++) {
            if (peerBitfield.hasPiece(i)) {
                PieceStatus status = statusMap.get(i);
                if (status == PieceStatus.MISSING || status == PieceStatus.FAILED) {
                    statusMap.put(i, PieceStatus.REQUESTED);
                    requestTimestamps.put(i, now);
                    return i;
                }
            }
        }

        // 2. Check for timed-out REQUESTED or DOWNLOADING pieces (> 10s)
        for (int i = 0; i < metadata.getTotalPieces(); i++) {
            if (peerBitfield.hasPiece(i)) {
                PieceStatus status = statusMap.get(i);
                if (status == PieceStatus.REQUESTED || status == PieceStatus.DOWNLOADING) {
                    Long reqTime = requestTimestamps.get(i);
                    if (reqTime != null && (now - reqTime > 10_000L)) {
                        statusMap.put(i, PieceStatus.REQUESTED);
                        requestTimestamps.put(i, now);
                        return i;
                    }
                }
            }
        }

        return null; // No eligible piece currently available from this peer
    }

    public synchronized void markDownloading(int index) {
        statusMap.put(index, PieceStatus.DOWNLOADING);
        requestTimestamps.put(index, System.currentTimeMillis());
    }

    public synchronized void markVerified(int index) {
        statusMap.put(index, PieceStatus.VERIFIED);
        requestTimestamps.remove(index);
    }

    public synchronized void markFailed(int index) {
        statusMap.put(index, PieceStatus.FAILED);
        requestTimestamps.remove(index);
    }

    public synchronized boolean isAllVerified() {
        for (int i = 0; i < metadata.getTotalPieces(); i++) {
            if (statusMap.get(i) != PieceStatus.VERIFIED) {
                return false;
            }
        }
        return true;
    }

    public synchronized int getCompletedPieceCount() {
        int count = 0;
        for (int i = 0; i < metadata.getTotalPieces(); i++) {
            if (statusMap.get(i) == PieceStatus.VERIFIED) {
                count++;
            }
        }
        return count;
    }

    public synchronized PieceStatus getStatus(int index) {
        return statusMap.get(index);
    }
}
