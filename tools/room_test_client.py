"""A small LAN guest used to exercise KaoToFace's room protocol over adb forward."""
import json
import select
import socket
import sys
import time


def main():
    host = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 34821
    name = sys.argv[3] if len(sys.argv) > 3 else "测试成员"

    sock = socket.create_connection((host, port), timeout=8)
    sock.setblocking(False)
    buf = b""

    def send(obj):
        sock.sendall((json.dumps(obj, ensure_ascii=False) + "\n").encode("utf-8"))
        print(">>", json.dumps(obj, ensure_ascii=False), flush=True)

    send({"t": "hello", "ver": 1, "name": name})
    sent_rejected = False
    sent_accepted = False
    started = time.time()
    lines_seen = 0
    seconds = int(sys.argv[4]) if len(sys.argv) > 4 else 55
    deadline = started + seconds

    while time.time() < deadline:
        r, _, _ = select.select([sock], [], [], 0.4)
        if r:
            try:
                chunk = sock.recv(65536)
            except BlockingIOError:
                continue
            if not chunk:
                print("CLOSED", flush=True)
                break
            buf += chunk
            while b"\n" in buf:
                raw, buf = buf.split(b"\n", 1)
                if not raw.strip():
                    continue
                try:
                    msg = json.loads(raw.decode("utf-8"))
                except Exception:
                    print("BAD", raw, flush=True)
                    continue
                if msg.get("t") == "line":
                    lines_seen += 1
                    line = msg.get("line") or {}
                    print(f"[LINE {lines_seen}] {line.get('speaker')}: {line.get('en')} / {line.get('zh')}", flush=True)
                else:
                    print(json.dumps(msg, ensure_ascii=False), flush=True)
                if msg.get("t") == "presence" and msg.get("guestMic") and not sent_accepted:
                    time.sleep(0.6)
                    send({
                        "t": "line",
                        "line": {
                            "id": "guest-line-" + str(int(time.time())),
                            "en": "The room sync is working across the network.",
                            "zh": "房间的网络同步正常。",
                            "src": "en",
                            "ts": int(time.time() * 1000),
                        },
                    })
                    sent_accepted = True
        if not sent_rejected and time.time() - started > 1.2:
            send({
                "t": "line",
                "line": {
                    "id": "guest-line-rejected",
                    "en": "This line should be rejected while guest microphone is off.",
                    "zh": "在成员收音关闭时，这句话应该被拒绝。",
                    "src": "en",
                    "ts": int(time.time() * 1000),
                },
            })
            sent_rejected = True

    print(f"DONE lines_seen={lines_seen}", flush=True)
    try:
        sock.sendall((json.dumps({"t": "bye"}, ensure_ascii=False) + "\n").encode())
        sock.close()
    except Exception:
        pass


if __name__ == "__main__":
    main()
