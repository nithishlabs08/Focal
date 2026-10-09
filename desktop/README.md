# Focal desktop receiver (LAN)

Minimal **receive-only** CLI viewer for FOCL streams from the **Focal phone app**. Desktop does not send camera video.

## Requirements

- Python 3.10+
- Same Wi‑Fi as the phone
- Pairing PIN shown in the Focal app when streaming starts

## Run

```bash
python3 focl_receiver.py --host 192.168.1.50 --port 8080 --pin 123456
```

This connects with `AUTH <pin>`, reads encrypted FOCL packets, and writes received H.264 to `focal_capture.h264` for playback in VLC.

Protocol details match `app/src/main/java/com/focal/android/transport/StreamPacket.kt` and `FocalSessionCrypto.kt` in the Android repo.
