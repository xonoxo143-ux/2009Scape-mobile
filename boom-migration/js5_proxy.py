#!/usr/bin/env python3
"""Small localhost JS5 relay used only for Boom migration analysis.

The Boom client connects to localhost. This proxy relays its cache connection to
the public Boom JS5 endpoint while recording byte counts and the initial frames.
It does not handle login credentials and the migration workflow never attempts a
login; the goal is only to let the client populate enough cache to reach its
normal title/login state.
"""

import argparse
import pathlib
import select
import socket
import threading
import time


def log_line(fp, text: str) -> None:
    stamp = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    fp.write(f"{stamp} {text}\n")
    fp.flush()


def relay_pair(client: socket.socket, upstream_host: str, upstream_port: int,
               outdir: pathlib.Path, conn_id: int) -> None:
    upstream = socket.create_connection((upstream_host, upstream_port), timeout=10)
    client.setblocking(False)
    upstream.setblocking(False)

    c2s_path = outdir / f"conn-{conn_id:03d}-client-to-server.bin"
    s2c_path = outdir / f"conn-{conn_id:03d}-server-to-client.bin"
    log_path = outdir / f"conn-{conn_id:03d}.log"

    totals = {client: 0, upstream: 0}
    started = time.monotonic()

    with c2s_path.open("wb") as c2s, s2c_path.open("wb") as s2c, log_path.open("w") as log:
        log_line(log, f"OPEN upstream={upstream_host}:{upstream_port}")
        sockets = [client, upstream]
        try:
            while sockets and time.monotonic() - started < 120:
                try:
                    readable, _, exceptional = select.select(sockets, [], sockets, 1.0)
                except OSError as exc:
                    log_line(log, f"SELECT_ERROR {type(exc).__name__}: {exc}")
                    break
                if exceptional:
                    log_line(log, "SOCKET_EXCEPTION")
                    break
                if not readable:
                    continue
                for src in list(readable):
                    try:
                        data = src.recv(65536)
                    except BlockingIOError:
                        continue
                    except (ConnectionResetError, OSError) as exc:
                        label = "CLIENT" if src is client else "UPSTREAM"
                        log_line(log, f"{label}_RESET {type(exc).__name__}: {exc}")
                        sockets = []
                        break
                    if not data:
                        label = "CLIENT" if src is client else "UPSTREAM"
                        log_line(log, f"{label}_EOF")
                        sockets = []
                        break
                    if src is client:
                        dst, capture, label = upstream, c2s, "C2S"
                    else:
                        dst, capture, label = client, s2c, "S2C"
                    capture.write(data)
                    capture.flush()
                    totals[src] += len(data)
                    # Keep logs useful without dumping cache payloads to stdout.
                    preview = data[:64].hex()
                    log_line(log, f"{label} bytes={len(data)} preview={preview}")
                    view = memoryview(data)
                    while view:
                        try:
                            sent = dst.send(view)
                            view = view[sent:]
                        except BlockingIOError:
                            time.sleep(0.001)
                        except (BrokenPipeError, ConnectionResetError, OSError) as exc:
                            log_line(log, f"SEND_ERROR {type(exc).__name__}: {exc}")
                            sockets = []
                            break
        finally:
            log_line(log, f"CLOSE c2s={totals[client]} s2c={totals[upstream]}")
            try:
                upstream.close()
            except OSError:
                pass
            try:
                client.close()
            except OSError:
                pass


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--listen-host", default="127.0.0.1")
    ap.add_argument("--listen-port", type=int, default=43594)
    ap.add_argument("--upstream-host", default="world1.boom-ps.com")
    ap.add_argument("--upstream-port", type=int, default=43594)
    ap.add_argument("--out", type=pathlib.Path, required=True)
    args = ap.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind((args.listen_host, args.listen_port))
    listener.listen(8)
    print(f"LISTEN {args.listen_host}:{args.listen_port} -> {args.upstream_host}:{args.upstream_port}", flush=True)

    conn_id = 0
    try:
        while True:
            client, addr = listener.accept()
            conn_id += 1
            print(f"ACCEPT #{conn_id} {addr}", flush=True)
            t = threading.Thread(
                target=relay_pair,
                args=(client, args.upstream_host, args.upstream_port, args.out, conn_id),
                daemon=True,
            )
            t.start()
    finally:
        listener.close()


if __name__ == "__main__":
    main()
