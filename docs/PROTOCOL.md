# Focal LAN streaming protocol (v1.1)

## Roles

| Role | Who |
|------|-----|
| **Sender (host)** | **Focal mobile app** on a phone/tablet only — camera, screen, and audio capture. |
| **Receiver (viewer)** | **Focal TV** app, **Focal mobile → Receive** tab, **desktop CLI** (`desktop/focl_receiver.py`). |

TV and desktop do **not** host camera streams.

## Discovery

- **mDNS type:** `_focal._tcp.`
- **TXT attributes:** `pin`, `port` (FOCL plain TCP, default 8080), `tls_port` (optional, default 8443), `tls_fp` (SHA-256 of server cert, Base64), `enc=focl-aes-gcm`, `sources=camera,screen,audio`

## Connection (FOCL clients — TV, desktop CLI)

1. TCP connect to `port` or `tls_port` (TLS with trust-on-first-use; embedded LAN cert on mobile).
2. Send line: `AUTH <4-digit-pin>\n`
3. Read line: `AUTH_OK ENC1` (encryption enabled) or `AUTH_ERR …`
4. Read binary **FOCL** frames until disconnect.

## FOCL packet

See `StreamPacket.kt` — magic `FOCL`, version `1`, codec byte, type byte, flags, timestamp µs, length, payload.

**Flags:** keyframe `0x01`, codec config `0x02`, AES-GCM payload `0x04`.

**Encryption:** key = `SHA-256("FOCL:" + pin)`; payload = `12-byte IV || ciphertext+tag` (AES-GCM).

**Types:** `VIDEO_NAL` (1), `AUDIO_RAW` (2), `HEARTBEAT` (6). PCM: 48 kHz mono 16-bit LE.

## HTTP (VLC / browsers)

- `GET /stream.h264` with header `X-Focal-Pin: <pin>` or `Authorization: Bearer <pin>`
- Raw Annex-B H.264 (not FOCL); not encrypted on the wire.

## Automation (no main UI)

```bash
# Camera stream
adb shell am broadcast -a com.focal.android.action.START_CAMERA_STREAM

# Screen (opens system capture consent activity only)
adb shell am broadcast -a com.focal.android.action.START_SCREEN_STREAM

# Audio-only
adb shell am broadcast -a com.focal.android.action.START_AUDIO_STREAM

# Stop
adb shell am broadcast -a com.focal.android.action.STOP_STREAM
```

Optional extras: `--es connection_mode WIFI`, `--es stream_mode VIDEO_AND_AUDIO`, `--es pairing_pin 123456`
