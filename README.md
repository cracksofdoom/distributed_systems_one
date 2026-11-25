# Multicast Chat

A small Java distributed systems demo with a central RMI sequencer and UDP multicast clients. The server assigns each message a global sequence number. Clients display messages in that order and request missing messages from the server when they detect a gap.

## Requirements

- JDK 17 or newer
- A network that supports IPv4 UDP multicast
- GNU Make for the short commands below (optional; `./gradlew` works directly)

The Gradle wrapper downloads Gradle automatically. No separate Gradle installation is needed.

## Run locally

Open separate terminals in the project directory:

```sh
make server
make client NAME=alice
make client NAME=bob
```

Type in the message field and press Enter or click **Send**. Each client name must be unique while connected. Close a window to leave the group. Stop the server with Ctrl+C.

To build and test:

```sh
make build
make test
```

The equivalent Gradle commands are `./gradlew runServer`, `./gradlew run --args="--name alice"`, and `./gradlew build`.

## Run on multiple machines

On the server, advertise an address that clients can reach:

```sh
make server ADVERTISE_HOST=192.168.1.10
```

On each client:

```sh
make client HOST=192.168.1.10 NAME=alice
```

Allow TCP ports **1099** (RMI registry) and **1100** (RMI service), plus UDP port **6789** (multicast). Clients must be able to join multicast group `234.20.7.1`; the server sends with TTL 1, so use the same local network. Some VPNs, Wi-Fi networks, and containers block multicast.

All settings can be changed with Make variables: `PORT`, `SERVICE_PORT`, `GROUP`, `MULTICAST_PORT`, `HOST`, `NAME`, and `ADVERTISE_HOST`. When changing the multicast port, use the same `MULTICAST_PORT` on server and clients. The server accepts `--port`, `--service-port`, `--group`, `--multicast-port`, and `--advertise-host`; the client accepts `--host`, `--port`, `--name`, and `--multicast-port`.

## Design and limits

- The server is the single source of sequence numbers. A client joining later starts at the next sequence and does not replay earlier chat history.
- The server keeps the latest 1,024 messages in memory for gap recovery. If a gap has aged out, the client reports it and resumes at the next received message.
- Each message body is limited to 4,096 UTF-8 bytes and each sender name to 64 UTF-8 bytes. The wire format is versioned and length checked.
- This is a local-network teaching application. State is not persisted, and RMI and multicast traffic are not encrypted or authenticated. Do not expose its ports to the public internet.

The main components are `SequencerServer` (RMI service startup), `SequencerImpl` (ordering and history), `Group` (multicast receive loop), `ChatClient` (gap recovery), and `GUI` (Swing view). Tests cover encoding, validation, history eviction, and sequence assignment. GitHub Actions runs the Gradle build on pushes and pull requests.
