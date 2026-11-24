"""Concatenate TTS clips into one 16k mono PCM WAV conversation for demo/testing."""
import os
import struct
import sys
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
CLIPS = os.path.join(HERE, "clips")
OUT = os.path.join(HERE, "conversation.wav")
GAP_SECONDS = 0.85
LEAD_SECONDS = 0.4


def read_frames(path):
    with wave.open(path, "rb") as w:
        assert w.getframerate() == 16000, (path, w.getframerate())
        assert w.getnchannels() == 1, (path, w.getnchannels())
        assert w.getsampwidth() == 2, (path, w.getsampwidth())
        return w.readframes(w.getnframes())


def main():
    names = sorted(n for n in os.listdir(CLIPS) if n.endswith(".wav"))
    if not names:
        raise SystemExit("no clips found; run gen_test_audio.ps1 first")
    silence = b"\x00\x00" * int(16000 * GAP_SECONDS)
    lead = b"\x00\x00" * int(16000 * LEAD_SECONDS)
    frames = [lead]
    for i, name in enumerate(names):
        frames.append(read_frames(os.path.join(CLIPS, name)))
        frames.append(silence)
    data = b"".join(frames)
    with wave.open(OUT, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(data)
    print("wrote", OUT, len(data) / 32000.0, "seconds")


if __name__ == "__main__":
    main()
