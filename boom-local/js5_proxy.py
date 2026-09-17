#!/usr/bin/env python3
import socket
import threading
import time
from pathlib import Path

LISTEN_HOST = '127.0.0.1'
LISTEN_PORT = 43594
REMOTE_HOST = 'world1.boom-ps.com'
REMOTE_PORT = 43594
OUT = Path(__file__).resolve().parent / 'proxy-output'
OUT.mkdir(exist_ok=True)


def log(line: str):
    with (OUT / 'proxy.log').open('a', encoding='utf-8') as f:
        f.write(f'{time.time():.3f} {line}\n')


def decode_client_chunk(data: bytes):
    if not data:
        return
    if len(data) == 21 and data[0] == 0x0F:
        rev = int.from_bytes(data[1:5], 'big')
        log(f'C>S handshake revision={rev} hex={data.hex()}')
        return
    if len(data) % 4 == 0:
        for i in range(0, len(data), 4):
            p = data[i:i+4]
            op = p[0]
            val = int.from_bytes(p[1:4], 'big')
            if op in (0, 1):
                archive = (val >> 16) & 0xFF
                group = val & 0xFFFF
                log(f'C>S request priority={op} archive={archive} group={group} hex={p.hex()}')
            else:
                log(f'C>S control opcode={op} value={val} hex={p.hex()}')
    else:
        log(f'C>S bytes={len(data)} hex={data[:256].hex()}')


def pump(src: socket.socket, dst: socket.socket, direction: str, rawfile: Path):
    with rawfile.open('ab') as raw:
        while True:
            try:
                data = src.recv(65536)
            except OSError as e:
                log(f'{direction} recv-error={e!r}')
                break
            if not data:
                log(f'{direction} eof')
                break
            raw.write(data)
            raw.flush()
            if direction == 'C>S':
                decode_client_chunk(data)
            else:
                log(f'S>C bytes={len(data)} head={data[:32].hex()}')
            try:
                dst.sendall(data)
            except OSError as e:
                log(f'{direction} send-error={e!r}')
                break
    try:
        dst.shutdown(socket.SHUT_WR)
    except OSError:
        pass


def handle(client: socket.socket, addr):
    log(f'accepted {addr[0]}:{addr[1]}')
    try:
        remote = socket.create_connection((REMOTE_HOST, REMOTE_PORT), timeout=10)
    except OSError as e:
        log(f'remote-connect-error={e!r}')
        client.close()
        return
    stamp = int(time.time() * 1000)
    t1 = threading.Thread(target=pump, args=(client, remote, 'C>S', OUT / f'client-{stamp}.bin'), daemon=True)
    t2 = threading.Thread(target=pump, args=(remote, client, 'S>C', OUT / f'server-{stamp}.bin'), daemon=True)
    t1.start(); t2.start()
    t1.join(); t2.join()
    client.close(); remote.close()
    log('connection-closed')


def main():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((LISTEN_HOST, LISTEN_PORT))
        s.listen(8)
        log(f'listening {LISTEN_HOST}:{LISTEN_PORT} -> {REMOTE_HOST}:{REMOTE_PORT}')
        while True:
            c, a = s.accept()
            threading.Thread(target=handle, args=(c, a), daemon=True).start()


if __name__ == '__main__':
    main()
