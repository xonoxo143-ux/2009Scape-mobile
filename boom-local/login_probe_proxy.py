#!/usr/bin/env python3
import argparse
import binascii
import select
import socket
import threading
import time
from pathlib import Path


def relay(a, b, log):
    sockets = [a, b]
    try:
        while True:
            r, _, _ = select.select(sockets, [], [], 15)
            if not r:
                return
            for s in r:
                data = s.recv(65536)
                if not data:
                    return
                other = b if s is a else a
                other.sendall(data)
    except OSError as e:
        log(f'RELAY_END {e!r}')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--listen', type=int, default=43594)
    ap.add_argument('--upstream-host', default='world1.boom-ps.com')
    ap.add_argument('--upstream-port', type=int, default=43594)
    ap.add_argument('--out', default='boom-local/login-report')
    args = ap.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    lock = threading.Lock()

    def log(msg):
        line = f'{time.time():.3f} {msg}'
        with lock:
            with (out / 'proxy.log').open('a', encoding='utf-8') as f:
                f.write(line + '\n')
        print(line, flush=True)

    def handle(c, addr):
        with c:
            c.settimeout(10)
            try:
                first = c.recv(5, socket.MSG_PEEK)
            except OSError as e:
                log(f'PEEK_FAIL {addr} {e!r}')
                return
            if not first:
                return
            op = first[0]
            log(f'CONNECT {addr[0]}:{addr[1]} first={binascii.hexlify(first).decode()} op={op}')

            # JS5 handshake begins with opcode 15. Forward these connections unchanged
            # so the client can populate its normal cache while the login path remains local.
            if op == 15:
                try:
                    upstream = socket.create_connection((args.upstream_host, args.upstream_port), timeout=10)
                except OSError as e:
                    log(f'JS5_UPSTREAM_FAIL {e!r}')
                    return
                log('JS5_PROXY_START')
                with upstream:
                    relay(c, upstream, log)
                log('JS5_PROXY_END')
                return

            # Modern OSRS login initial request is opcode 14. Keep it local, return
            # success + a deterministic server seed, then capture the encrypted login block.
            if op == 14:
                initial = c.recv(64)
                log(f'LOGIN_INITIAL len={len(initial)} hex={initial.hex()}')
                (out / 'login-initial.bin').write_bytes(initial)
                seed = bytes.fromhex('1122334455667788')
                c.sendall(b'\x00' + seed)
                log(f'LOGIN_SEED_SENT hex={seed.hex()}')
                c.settimeout(8)
                chunks = []
                total = 0
                try:
                    while total < 65536:
                        d = c.recv(min(4096, 65536-total))
                        if not d:
                            break
                        chunks.append(d)
                        total += len(d)
                        # Login packet has a length prefix; a short idle is enough for capture.
                        if total >= 3:
                            time.sleep(0.25)
                            c.settimeout(0.5)
                except socket.timeout:
                    pass
                data = b''.join(chunks)
                (out / 'login-after-seed.bin').write_bytes(data)
                log(f'LOGIN_AFTER_SEED len={len(data)} hex={data.hex()}')
                return

            # Unknown traffic is recorded rather than forwarded so we don't accidentally
            # authenticate or send gameplay traffic upstream.
            data = c.recv(4096)
            log(f'UNKNOWN_LOCAL_ONLY op={op} len={len(data)} hex={data.hex()}')

    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('127.0.0.1', args.listen))
    s.listen(32)
    log(f'LISTEN 127.0.0.1:{args.listen}')
    while True:
        c, addr = s.accept()
        threading.Thread(target=handle, args=(c, addr), daemon=True).start()


if __name__ == '__main__':
    main()
