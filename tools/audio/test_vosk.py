"""Offline check of Vosk small models + language routing scores on the TTS conversation."""
import json
import os
import sys
import time

from vosk import Model, KaldiRecognizer, SetLogLevel

SetLogLevel(-1)
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
WAV = os.path.join(HERE, "conversation.wav")
MODELS = {
    "en": os.path.join(ROOT, "app", "src", "main", "assets", "vosk", "en"),
    "zh": os.path.join(ROOT, "app", "src", "main", "assets", "vosk", "zh"),
}


def mean_conf(res):
    words = res.get("result") or []
    if not words:
        return 0.0
    return sum(w.get("conf", 0.0) for w in words) / len(words)


def run(lang, path):
    print(f"loading {lang} ...", flush=True)
    model = Model(path)
    rec = KaldiRecognizer(model, 16000)
    rec.SetWords(True)
    events = []
    t0 = time.time()
    with open(WAV, "rb") as f:
        f.read(44)
        while True:
            data = f.read(4000)
            if not data:
                break
            if rec.AcceptWaveform(data):
                res = json.loads(rec.Result())
                if res.get("text", "").strip():
                    events.append(
                        ("final", round(time.time() - t0, 2), res.get("text", ""), round(mean_conf(res), 3), len(res.get("result") or []))
                    )
                    print(f"[{lang}] FINAL @{events[-1][1]} conf={events[-1][3]} words={events[-1][4]}: {events[-1][2]}", flush=True)
            else:
                part = json.loads(rec.PartialResult()).get("partial", "")
                if part.strip():
                    events.append(("partial", round(time.time() - t0, 2), part, 0, 0))
    res = json.loads(rec.FinalResult())
    if res.get("text", "").strip():
        events.append(("final", round(time.time() - t0, 2), res.get("text", ""), round(mean_conf(res), 3), len(res.get("result") or [])))
        print(f"[{lang}] FINAL @{events[-1][1]} conf={events[-1][3]} words={events[-1][4]}: {events[-1][2]}", flush=True)
    print(f"[{lang}] done in {time.time() - t0:.2f}s, {len(events)} events", flush=True)
    return events


def main():
    all_events = {lang: run(lang, path) for lang, path in MODELS.items()}
    out = os.path.join(HERE, "vosk_events.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(all_events, f, ensure_ascii=False, indent=1)
    print("wrote", out)


if __name__ == "__main__":
    main()
