#!/usr/bin/env python3
import argparse
import http.server
import pathlib
import socket
import socketserver
import threading
import time

ROOT = pathlib.Path(__file__).resolve().parent
OUT = ROOT / "probe-output"
OUT.mkdir(exist_ok=True)

class ConfigHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        with (OUT / "http-requests.log").open("a", encoding="utf-8") as f:
            f.write(f"GET {self.path}\n")
        if self.path.startswith("/jav_config.ws"):
            data = (ROOT / "jav_config.local.ws").read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        else:
            self.send_error(404)

    def log_message(self, fmt, *args):
        with (OUT / "http-server.log").open("a", encoding="utf-8") as f:
            f.write((fmt % args) + "\n")


def recv_some(conn, limit, timeout=2.0):
    conn.settimeout(timeout)
    chunks = []
    total = 0
    while total < limit:
        try:
            part = conn.recv(min(4096, limit - total))
        except socket.timeout:
            break
        if not part:
            break
        chunks.append(part)
        total += len(part)
        if len(part) < 4096:
            break
    return b"".join(chunks)


def tcp_probe(port: int):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(("127.0.0.1", port))
        s.listen(8)
        s.settimeout(1.0)
        deadline = time.time() + 45
        while time.time() < deadline:
            try:
                conn, addr = s.accept()
            except socket.timeout:
                continue
            with conn:
                first = recv_some(conn, 4096, 2.0)
                stamp = int(time.time() * 1000)
                with (OUT / "tcp-connections.log").open("a", encoding="utf-8") as f:
                    f.write(f"from={addr[0]}:{addr[1]} phase=handshake bytes={len(first)} hex={first.hex()}\n")
                (OUT / f"tcp-handshake-{stamp}.bin").write_bytes(first)

                # Modern JS5 accepts the revision handshake with a single zero byte.
                # If this is the expected protocol, the client should immediately send
                # its post-handshake control/request packets on the same connection.
                try:
                    conn.sendall(b"\x00")
                except OSError as e:
                    with (OUT / "tcp-connections.log").open("a", encoding="utf-8") as f:
                        f.write(f"from={addr[0]}:{addr[1]} phase=accept-send error={e!r}\n")
                    continue

                following = recv_some(conn, 65536, 8.0)
                with (OUT / "tcp-connections.log").open("a", encoding="utf-8") as f:
                    f.write(f"from={addr[0]}:{addr[1]} phase=after-accept bytes={len(following)} hex={following.hex()}\n")
                (OUT / f"tcp-after-accept-{stamp}.bin").write_bytes(following)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--http", type=int, default=8080)
    ap.add_argument("--game", type=int, default=43594)
    args = ap.parse_args()

    t = threading.Thread(target=tcp_probe, args=(args.game,), daemon=True)
    t.start()

    with socketserver.TCPServer(("127.0.0.1", args.http), ConfigHandler) as httpd:
        httpd.timeout = 1
        deadline = time.time() + 45
        while time.time() < deadline:
            httpd.handle_request()

if __name__ == "__main__":
    main()
