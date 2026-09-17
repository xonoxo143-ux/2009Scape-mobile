#!/usr/bin/env python3
import argparse
import json
import socket
import threading
from pathlib import Path

HOST = '127.0.0.1'
PORT = 43594
REMOTE_HOST = 'world1.boom-ps.com'
REMOTE_PORT = 43594
HANDSHAKE_LEN = 21


def recv_exact(sock, n):
    out = bytearray()
    while len(out) < n:
        chunk = sock.recv(n - len(out))
        if not chunk:
            raise EOFError(f'eof after {len(out)}/{n}')
        out.extend(chunk)
    return bytes(out)


def response_wire_length(compression, length):
    payload = length + (4 if compression != 0 else 0)
    first = min(payload, 504)
    remaining = payload - first
    if remaining <= 0:
        return 8 + first
    blocks = (remaining + 510) // 511
    return 8 + first + remaining + blocks


def read_js5_response(sock):
    header = recv_exact(sock, 8)
    archive = header[0]
    group = int.from_bytes(header[1:3], 'big')
    compression = header[3]
    length = int.from_bytes(header[4:8], 'big')
    total = response_wire_length(compression, length)
    rest = recv_exact(sock, total - 8)
    return archive, group, header + rest


def save_response(root, archive, group, wire):
    d = root / f'{archive:03d}'
    d.mkdir(parents=True, exist_ok=True)
    (d / f'{group:05d}.bin').write_bytes(wire)


def load_response(root, archive, group):
    p = root / f'{archive:03d}' / f'{group:05d}.bin'
    return p.read_bytes() if p.exists() else None


def record(root, log_path):
    root.mkdir(parents=True, exist_ok=True)
    with log_path.open('w', buffering=1) as log, socket.socket() as ls:
        ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        ls.bind((HOST, PORT)); ls.listen(1)
        log.write(f'RECORD listening {HOST}:{PORT}\n')
        client, addr = ls.accept()
        log.write(f'client {addr}\n')
        with client, socket.create_connection((REMOTE_HOST, REMOTE_PORT), 10) as remote:
            hs = recv_exact(client, HANDSHAKE_LEN)
            remote.sendall(hs)
            accept = recv_exact(remote, 1)
            client.sendall(accept)
            log.write(f'handshake={hs.hex()} accept={accept.hex()}\n')

            stop = threading.Event()
            pending_log = log

            def c2s():
                try:
                    while not stop.is_set():
                        req = recv_exact(client, 4)
                        remote.sendall(req)
                        op = req[0]
                        value = int.from_bytes(req[1:4], 'big')
                        if op in (0, 1):
                            archive = (value >> 16) & 0xff
                            group = value & 0xffff
                            pending_log.write(f'REQ op={op} a={archive} g={group}\n')
                        else:
                            pending_log.write(f'CTL op={op} value={value}\n')
                except Exception as e:
                    pending_log.write(f'c2s stop {e!r}\n')
                    stop.set()

            t = threading.Thread(target=c2s, daemon=True)
            t.start()
            try:
                while not stop.is_set():
                    archive, group, wire = read_js5_response(remote)
                    save_response(root, archive, group, wire)
                    log.write(f'RESP a={archive} g={group} bytes={len(wire)}\n')
                    client.sendall(wire)
            except Exception as e:
                log.write(f's2c stop {e!r}\n')
                stop.set()
            t.join(timeout=1)


def serve(root, log_path):
    with log_path.open('w', buffering=1) as log, socket.socket() as ls:
        ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        ls.bind((HOST, PORT)); ls.listen(1)
        log.write(f'SERVE listening {HOST}:{PORT}\n')
        client, addr = ls.accept()
        log.write(f'client {addr}\n')
        with client:
            hs = recv_exact(client, HANDSHAKE_LEN)
            rev = int.from_bytes(hs[1:5], 'big') if hs and hs[0] == 0x0f else -1
            log.write(f'handshake revision={rev} hex={hs.hex()}\n')
            client.sendall(b'\x00')
            while True:
                try:
                    req = recv_exact(client, 4)
                except EOFError:
                    break
                op = req[0]
                value = int.from_bytes(req[1:4], 'big')
                if op not in (0, 1):
                    log.write(f'CTL op={op} value={value}\n')
                    continue
                archive = (value >> 16) & 0xff
                group = value & 0xffff
                wire = load_response(root, archive, group)
                if wire is None:
                    log.write(f'MISS op={op} a={archive} g={group}\n')
                    # Keep socket alive so the test log shows all missing requests it can expose.
                    continue
                log.write(f'HIT op={op} a={archive} g={group} bytes={len(wire)}\n')
                client.sendall(wire)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('mode', choices=['record', 'serve'])
    ap.add_argument('root', type=Path)
    ap.add_argument('--log', type=Path, required=True)
    args = ap.parse_args()
    args.log.parent.mkdir(parents=True, exist_ok=True)
    if args.mode == 'record':
        record(args.root, args.log)
    else:
        serve(args.root, args.log)


if __name__ == '__main__':
    main()
