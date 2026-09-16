#!/usr/bin/env python3
import argparse
import pathlib
import socket
import threading
import time


def serve(port: int, outdir: pathlib.Path, stop: threading.Event) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(("127.0.0.1", port))
        s.listen(16)
        s.settimeout(0.5)
        print(f"LISTEN 127.0.0.1:{port}", flush=True)
        n = 0
        while not stop.is_set():
            try:
                conn, addr = s.accept()
            except socket.timeout:
                continue
            n += 1
            path = outdir / f"tcp-{port}-{n}.bin"
            print(f"ACCEPT {port} #{n} from {addr}", flush=True)
            conn.settimeout(2.0)
            data = bytearray()
            try:
                while len(data) < 65536:
                    chunk = conn.recv(4096)
                    if not chunk:
                        break
                    data.extend(chunk)
                    print(f"RX {port} #{n}: {chunk.hex()}", flush=True)
                    # Keep the connection open briefly. We intentionally do not
                    # pretend to be a valid server yet; this probe only records
                    # the client's first handshake bytes.
                    if len(data) >= 1:
                        time.sleep(1.0)
                        break
            except socket.timeout:
                pass
            path.write_bytes(data)
            conn.close()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="probe/captures")
    ap.add_argument("--ports", default="43594")
    args = ap.parse_args()

    outdir = pathlib.Path(args.out)
    outdir.mkdir(parents=True, exist_ok=True)
    stop = threading.Event()
    threads = []
    for value in args.ports.split(","):
        port = int(value)
        t = threading.Thread(target=serve, args=(port, outdir, stop), daemon=True)
        t.start()
        threads.append(t)
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        stop.set()
        for t in threads:
            t.join(timeout=2)


if __name__ == "__main__":
    main()
