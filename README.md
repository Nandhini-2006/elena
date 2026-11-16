# High-Performance Peer-to-Peer (P2P) File Distribution System

A decentralized, event-driven file-sharing platform akin to BitTorrent, built entirely in **Java 21** using low-level **Java NIO (Non-blocking I/O)**, custom binary framing protocols, asynchronous piece scheduling, concurrent multi-peer pipelining, and cryptographic SHA-256 integrity validation.

---

## 1. Architectural Highlights

- **Pure Java NIO Engine**: Built without third-party web frameworks (no Spring, Netty, or Akka). Utilizes `ServerSocketChannel`, `SocketChannel`, `Selector`, `SelectionKey`, and direct `ByteBuffer` manipulation to handle hundreds of concurrent connections with minimal threads.
- **Custom Binary Framing Protocol**: Zero Java object serialization. Uses a compact 19-byte binary header with magic bytes (`0x50325046`), versioning, message types, request tracking (`requestId`), and strictly bounded byte payloads to guard against deserialization attacks and buffer overruns.
- **Parallel Multi-Peer Pipelining**: Concurrently downloads non-overlapping chunks from multiple seeders/leechers in the swarm, dynamically handling slow or dropped peers.
- **Random-Access Positional Storage**: Leverages `FileChannel` positional reads and writes (`channel.write(buf, offset)`) to stream chunks directly to disk out-of-order without buffering entire multi-gigabyte files in RAM.
- **Cryptographic SHA-256 Integrity Engine**: Validates every single piece against the file manifest using `MessageDigest`. Corrupted or tampered pieces are discarded and rescheduled. The overall reconstructed file is re-verified upon completion.
- **Hybrid P2P Swarm Coordination**: Lightweight Tracker Server orchestrates peer discovery, liveness heartbeats, and swarm bitfields while leaving 100% of the payload data transfer directly between peers.

---

## 2. System Architecture & Component Design

```mermaid
graph TD
    subgraph Tracker Node
        TS[Tracker Server :6881]
        TR[Tracker Registry]
        TS --> TR
    end

    subgraph Peer A (Seeder)
        NA[NIO Server / Channel]
        UMA[Upload Manager]
        SMA[Storage Manager]
        NA --> UMA --> SMA
    end

    subgraph Peer B (Leecher / Seeder)
        NB[NIO Server / Client]
        DMB[Download Manager]
        SMB[Storage Manager]
        IMB[Integrity Manager]
        NB --> DMB
        DMB --> IMB --> SMB
    end

    subgraph Peer C (Leecher)
        NC[NIO Server / Client]
        DMC[Download Manager]
        SMC[Storage Manager]
        NC --> DMC --> SMC
    end

    %% Signaling
    NA -. "Announce / Heartbeat" .-> TS
    NB -. "Announce / Get Peers" .-> TS
    NC -. "Announce / Get Peers" .-> TS

    %% P2P Data Transfers
    NA == "Binary Protocol (Chunks)" ==> NB
    NA == "Binary Protocol (Chunks)" ==> NC
    NB == "Binary Protocol (Chunks)" ==> NC
```

---

## 3. Protocol Framing Specification

All peer-to-peer and tracker communication uses a compact binary framing format:

| Field | Size | Data Type | Description |
|---|---|---|---|
| **Magic Number** | 4 bytes | `0x50325046` | ASCII `"P2PF"`, identifies valid protocol frames |
| **Version** | 2 bytes | `short` | Protocol version (`0x0001`) |
| **Message Type** | 1 byte | `byte` | Opcode (1=Handshake, 2=Announce, 3=PeerList, 4=HavePiece, 5=RequestPiece, 6=PieceData, 7=Heartbeat, 8=Bitfield, 9=Error) |
| **Payload Length** | 4 bytes | `int` | Size of the payload in bytes (Max 16 MB) |
| **Request ID** | 8 bytes | `long` | Correlation identifier for matching requests and responses |
| **Payload** | $L$ bytes | `byte[]` | Message-specific encoded binary data |

---

## 4. Project Structure

```
c:\Users\admin\Documents\Elena
├── pom.xml
├── p2p-dist.jar                     # Standalone runnable JAR
├── README.md
├── src
│   ├── main/java/com/p2p/filedist
│   │   ├── Main.java                # Unified CLI & Swarm Demo entrypoint
│   │   ├── common/Constants.java    # Buffer sizes, protocol magic, defaults
│   │   ├── model/
│   │   │   ├── Bitfield.java        # Compact bitset representing piece possession
│   │   │   ├── FileMetadata.java    # Manifest (.meta) with piece hashes & sizes
│   │   │   ├── PeerInfo.java        # Peer address, port, and swarm status
│   │   │   ├── PieceInfo.java       # Piece index, byte offset, length, hash
│   │   │   └── PieceStatus.java     # MISSING, REQUESTED, DOWNLOADING, VERIFIED
│   │   ├── protocol/
│   │   │   ├── Message.java         # Encapsulated binary frame
│   │   │   ├── MessageCodec.java    # Type-safe encoders & decoders
│   │   │   ├── MessageHeader.java   # 19-byte binary header
│   │   │   └── MessageType.java     # Opcode enumeration
│   │   ├── net/
│   │   │   ├── NioConnection.java   # Non-blocking connection & stream framing
│   │   │   ├── NioServer.java       # Event-loop Selector accepting & reading channels
│   │   │   ├── NioClient.java       # Outbound channel factory & transport
│   │   │   └── MessageHandler.java  # Async network callback interface
│   │   ├── storage/
│   │   │   ├── StorageManager.java  # Random-access FileChannel reader/writer
│   │   │   └── FileChunker.java     # File partitioning & piece hash generation
│   │   ├── integrity/
│   │   │   └── IntegrityManager.java# Cryptographic SHA-256 verification
│   │   ├── tracker/
│   │   │   ├── TrackerServer.java   # Central coordination server
│   │   │   ├── TrackerRegistry.java # Swarm directory & stale peer eviction
│   │   │   └── TrackerClient.java   # Client helper to announce & fetch peers
│   │   ├── peer/
│   │   │   ├── PeerNode.java        # Complete peer daemon
│   │   │   ├── DownloadManager.java # Parallel downloading pipeline
│   │   │   ├── UploadManager.java   # Outbound piece server
│   │   │   └── PieceScheduler.java  # Dynamic chunk scheduler & timeout retry
│   │   └── cli/
│   │       └── PeerCli.java         # Interactive user terminal
│   └── test/java/com/p2p/filedist
│       ├── ProtocolTest.java
│       ├── StorageAndIntegrityTest.java
│       └── EndToEndP2PTest.java
```

---

## 5. How to Run

### Option A: Run the Automated Multi-Peer Swarm Demo
The demo automatically initializes:
1. A **Tracker Server** on a dynamic port
2. A **Seeder Peer (Node A)** that chunks and publishes a 1 MB file
3. A **Leecher Peer (Node B)** that downloads from Node A, validates SHA-256, and becomes a seeder
4. A **Second Leecher Peer (Node C)** that downloads concurrently from both Node A and Node B
5. Full SHA-256 verification and throughput reporting

```bash
java -jar p2p-dist.jar demo
```

### Option B: Run a Real Distributed Swarm

#### 1. Start Tracker Server
```bash
java -jar p2p-dist.jar tracker 6881
```

#### 2. Start Peer A (Publisher / Seeder)
```bash
java -jar p2p-dist.jar peer Seeder-1 0 127.0.0.1 6881 ./storage_seeder
```
Inside the interactive prompt:
```text
p2p> publish /path/to/my_video.mp4
```
This generates `my_video.mp4.meta` containing piece hashes and announces to the tracker.

#### 3. Start Peer B (Leecher)
```bash
java -jar p2p-dist.jar peer Leecher-2 0 127.0.0.1 6881 ./storage_leecher
```
Inside the interactive prompt:
```text
p2p> download /path/to/my_video.mp4.meta
```
Peer B fetches the swarm member list from the tracker, downloads pieces concurrently from available seeders, verifies hashes, and writes the reconstructed file to disk!
