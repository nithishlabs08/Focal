#!/usr/bin/env python3
"""Minimal Focal FOCL receiver — decrypts AES-GCM payloads and saves H.264 NALs."""

import argparse
import hashlib
import socket
import struct
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"FOCL"
HEADER_SIZE = 20
FLAG_ENCRYPTED = 0x04
VIDEO_NAL = 1


def derive_key(pin: str) -> bytes:
    return hashlib.sha256(f"FOCL:{pin}".encode()).digest()


def read_exact(sock: socket.socket, n: int) -> bytes | None:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            return None
        buf += chunk
    return buf


def skip_text_line(sock: socket.socket, prefix: bytes) -> bool:
    data = prefix
    while b"\n" not in data:
        more = sock.recv(1)
        if not more:
            return False
        data += more
    return True


def read_focl_frame(sock: socket.socket):
    prefix = read_exact(sock, 4)
    if prefix is None:
        return None
    while prefix != MAGIC:
        if not skip_text_line(sock, prefix):
            return None
        prefix = read_exact(sock, 4)
        if prefix is None:
            return None
    rest = read_exact(sock, HEADER_SIZE - 4)
    if rest is None:
        return None
    header = prefix + rest
    flags = header[7]
    type_code = header[6]
    ts = struct.unpack(">Q", header[8:16])[0]
    payload_len = struct.unpack(">I", header[16:20])[0]
    payload = read_exact(sock, payload_len)
    if payload is None:
        return None
    return type_code, flags, ts, payload


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--tls", action="store_true", help="Use TLS on --port (default 8443 if --port omitted)")
    parser.add_argument("--pin", required=True)
    parser.add_argument("--out", default="focal_capture.h264")
    args = parser.parse_args()

    aes = AESGCM(derive_key(args.pin))

    import ssl

    port = 8443 if args.tls and args.port == 8080 else args.port
    raw = socket.create_connection((args.host, port), timeout=10)
    if args.tls:
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        sock = ctx.wrap_socket(raw, server_hostname=args.host)
    else:
        sock = raw
    with sock:
        sock.sendall(f"AUTH {args.pin}\n".encode())
        line = b""
        while not line.endswith(b"\n"):
            chunk = sock.recv(1)
            if not chunk:
                raise SystemExit("auth failed: connection closed")
            line += chunk
        if not line.decode().strip().startswith("AUTH_OK"):
            raise SystemExit(f"auth failed: {line!r}")

        with open(args.out, "wb") as out:
            while True:
                frame = read_focl_frame(sock)
                if frame is None:
                    break
                type_code, flags, _ts, payload = frame
                if flags & FLAG_ENCRYPTED:
                    iv = payload[:12]
                    body = payload[12:]
                    payload = aes.decrypt(iv, body, None)
                if type_code == VIDEO_NAL:
                    out.write(payload)


if __name__ == "__main__":
    main()
