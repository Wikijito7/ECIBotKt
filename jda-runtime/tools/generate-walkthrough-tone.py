#!/usr/bin/env python3
"""Generate the committed 75-packet walkthrough tone resource.

This is a one-off, reproducible generator for the Phase 2C diagnostic. It uses only the
Python standard library and the macOS built-in /usr/bin/afconvert: nothing is installed.

Why pre-encoded packets: on an Apple Silicon Mac JDA's bundled Opus encoder is x86_64-only,
so the diagnostic must send `isOpus=true` packets. afconvert can write CAF+Opus with
960-sample (20 ms) packets, but not Ogg Opus, so the packets are extracted from the CAF
`pakt`/`data` chunks.

The resource is a bare packet stream, not an audio container:
    repeated [big-endian uint16 packet length][one Opus packet]

Every packet's duration is derived from its Opus TOC byte, never from container metadata.
The output contains exactly 75 packets of 20 ms (~1.5 s) and rejects any packet larger than
1275 bytes. A 1.48 s faded tone is encoded so that Opus priming and remainder padding fill
exactly 75 packets; the whole fade-in/fade-out is preserved and no packet is trimmed.

Usage (from the repository root):
    python3 jda-runtime/tools/generate-walkthrough-tone.py

Regeneration may produce different bytes on a different macOS/afconvert version; the
committed resource is the canonical staging asset.
"""

from __future__ import annotations

import math
import shutil
import struct
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

SAMPLE_RATE = 48_000
CHANNELS = 2
DURATION_SECONDS = 1.48
FREQUENCY_HZ = 440.0
AMPLITUDE = 0.10
FADE_SECONDS = 0.010
FRAMES_PER_PACKET = 960
FRAME_MILLIS = 20
EXPECTED_PACKETS = 75
MAX_PACKET_BYTES = 1275

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_OUTPUT = SCRIPT_DIR.parent / "src" / "main" / "resources" / "audio" / "walkthrough-tone-440.opuspackets"


def write_tone_wav(path: Path) -> None:
    total_samples = int(SAMPLE_RATE * DURATION_SECONDS)
    fade_samples = int(SAMPLE_RATE * FADE_SECONDS)
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(CHANNELS)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        frames = bytearray()
        for index in range(total_samples):
            if index < fade_samples:
                envelope = index / fade_samples
            elif index >= total_samples - fade_samples:
                envelope = (total_samples - 1 - index) / fade_samples
            else:
                envelope = 1.0
            value = int(
                round(
                    math.sin(2.0 * math.pi * FREQUENCY_HZ * index / SAMPLE_RATE)
                    * AMPLITUDE
                    * envelope
                    * 32767
                )
            )
            frames += struct.pack("<hh", value, value)
        wav.writeframes(bytes(frames))


def encode_caf(wav_path: Path, caf_path: Path) -> None:
    afconvert = shutil.which("afconvert")
    if afconvert is None:
        raise SystemExit("afconvert is required (macOS built-in) and was not found")
    subprocess.run(
        [afconvert, "-f", "caff", "-d", "opus", "-c", str(CHANNELS), str(wav_path), str(caf_path)],
        check=True,
    )


def read_varint(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    for _ in range(10):
        byte = data[offset]
        offset += 1
        value = (value << 7) | (byte & 0x7F)
        if not byte & 0x80:
            return value, offset
    raise ValueError("invalid CAF variable-length integer")


def extract_packets(caf_path: Path) -> list[bytes]:
    data = caf_path.read_bytes()
    if data[:4] != b"caff":
        raise ValueError("not a CAF file")

    chunks: dict[bytes, bytes] = {}
    offset = 8
    while offset < len(data):
        chunk_type = data[offset:offset + 4]
        chunk_size = struct.unpack(">q", data[offset + 4:offset + 12])[0]
        if chunk_size < 0 or offset + 12 + chunk_size > len(data):
            raise ValueError(f"invalid CAF chunk {chunk_type!r}")
        chunks[chunk_type] = data[offset + 12:offset + 12 + chunk_size]
        offset += 12 + chunk_size

    pakt = chunks.get(b"pakt")
    audio = chunks.get(b"data")
    if pakt is None or audio is None:
        raise ValueError("CAF file is missing pakt or data")

    packet_count, valid_frames, priming_frames, remainder_frames = struct.unpack(">qqii", pakt[:24])
    cursor = 24
    values: list[int] = []
    while cursor < len(pakt):
        value, cursor = read_varint(pakt, cursor)
        values.append(value)

    if len(values) == packet_count:
        sizes = values
    elif len(values) == packet_count * 2:
        sizes = []
        for index in range(0, len(values), 2):
            size, frames = values[index], values[index + 1]
            if frames != FRAMES_PER_PACKET:
                raise ValueError(f"expected {FRAMES_PER_PACKET} frames per packet, got {frames}")
            sizes.append(size)
    else:
        raise ValueError(
            f"unsupported CAF packet table: {len(values)} values for {packet_count} packets"
        )

    edit_count = struct.unpack(">I", audio[:4])[0]
    if edit_count != 1:
        raise ValueError(f"expected exactly one CAF edit, got {edit_count}")
    payload = audio[4:]
    if sum(sizes) != len(payload):
        raise ValueError(f"packet sizes {sum(sizes)} do not match data length {len(payload)}")

    packets = []
    position = 0
    for size in sizes:
        packets.append(payload[position:position + size])
        position += size

    print(
        f"CAF: packets={packet_count} validFrames={valid_frames} "
        f"priming={priming_frames} remainder={remainder_frames} bytes={len(payload)}"
    )
    return packets


def opus_packet_duration_millis(packet: bytes) -> float:
    if not packet:
        raise ValueError("empty Opus packet")
    toc = packet[0]
    config = toc >> 3
    code = toc & 0x03

    if config < 12:
        frame_micros = (10_000, 20_000, 40_000, 60_000)[config & 0x03]
    elif config < 16:
        frame_micros = 10_000 if config in (12, 14) else 20_000
    else:
        frame_micros = (2_500, 5_000, 10_000, 20_000)[config & 0x03]

    if code == 0:
        frames = 1
    elif code in (1, 2):
        frames = 2
    else:
        if len(packet) < 2:
            raise ValueError("truncated code-3 Opus packet")
        frames = packet[1] & 0x3F
        if frames < 1:
            raise ValueError("code-3 Opus packet declares zero frames")

    return frames * frame_micros / 1000.0


def validate_packets(packets: list[bytes]) -> None:
    if len(packets) != EXPECTED_PACKETS:
        raise SystemExit(
            f"afconvert produced {len(packets)} packets; this generator expects exactly "
            f"{EXPECTED_PACKETS} so the whole faded tone fits the resource"
        )
    for index, packet in enumerate(packets):
        if len(packet) > MAX_PACKET_BYTES:
            raise SystemExit(f"packet {index} is {len(packet)} bytes, above the {MAX_PACKET_BYTES} limit")
        duration = opus_packet_duration_millis(packet)
        if abs(duration - FRAME_MILLIS) > 1e-9:
            raise SystemExit(f"packet {index} lasts {duration} ms, expected {FRAME_MILLIS} ms")


def write_resource(packets: list[bytes], output: Path) -> None:
    if len(packets) != EXPECTED_PACKETS:
        raise SystemExit(f"selected {len(packets)} packets, expected {EXPECTED_PACKETS}")
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        for packet in packets:
            handle.write(struct.pack(">H", len(packet)))
            handle.write(packet)
    print(f"wrote {len(packets)} packets ({output.stat().st_size} bytes) to {output}")


def main() -> int:
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_OUTPUT
    with tempfile.TemporaryDirectory(prefix="ecibot-tone-") as temp:
        temp_dir = Path(temp)
        wav_path = temp_dir / "tone.wav"
        caf_path = temp_dir / "tone.caf"
        write_tone_wav(wav_path)
        encode_caf(wav_path, caf_path)
        packets = extract_packets(caf_path)
        validate_packets(packets)
        write_resource(packets, output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
