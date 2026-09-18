#!/usr/bin/env python3
import argparse
import binascii
import select
import socket
import struct
import threading
import time
from pathlib import Path

from rev239_initial_world import build_initial_frame

ROOT = Path(__file__).resolve().parent
KEY_FILE = ROOT / 'local-rsa-test-key.properties'
MASK32 = 0xFFFFFFFF
XTEA_DELTA = 0x9E3779B9


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


def load_local_rsa():
    props = {}
    for raw in KEY_FILE.read_text(encoding='utf-8').splitlines():
        line = raw.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        k, v = line.split('=', 1)
        props[k.strip()] = v.strip()
    return int(props['modulus_decimal']), int(props['private_exponent_decimal'])


def xtea_decrypt(data: bytes, keys):
    out = bytearray(data)
    block_end = (len(out) // 8) * 8
    for off in range(0, block_end, 8):
        v0, v1 = struct.unpack_from('>II', out, off)
        total = (XTEA_DELTA * 32) & MASK32
        for _ in range(32):
            mix = (((((v0 << 4) & MASK32) ^ (v0 >> 5)) + v0) & MASK32) ^ ((total + keys[(total >> 11) & 3]) & MASK32)
            v1 = (v1 - mix) & MASK32
            total = (total - XTEA_DELTA) & MASK32
            mix = (((((v1 << 4) & MASK32) ^ (v1 >> 5)) + v1) & MASK32) ^ ((total + keys[total & 3]) & MASK32)
            v0 = (v0 - mix) & MASK32
        struct.pack_into('>II', out, off, v0, v1)
    return bytes(out)


def cstring(data: bytes, start=0):
    end = data.find(b'\x00', start)
    if end < 0:
        return None, start
    return data[start:end].decode('cp1252', errors='replace'), end + 1


def decrypt_login(packet: bytes, out: Path, log):
    if len(packet) < 20:
        log(f'LOGIN_PARSE_TOO_SHORT len={len(packet)}')
        return None
    opcode = packet[0]
    payload_len = int.from_bytes(packet[1:3], 'big')
    revision = int.from_bytes(packet[3:7], 'big')
    sub_revision = int.from_bytes(packet[7:11], 'big')
    client_revision = int.from_bytes(packet[11:15], 'big')
    client_type = packet[15]
    rsa_len = packet[19]
    rsa_start = 20
    rsa_end = rsa_start + rsa_len
    if rsa_end > len(packet):
        log(f'LOGIN_RSA_TRUNCATED rsa_len={rsa_len} packet_len={len(packet)}')
        return None

    modulus, private_exponent = load_local_rsa()
    cipher = packet[rsa_start:rsa_end]
    cipher_int = int.from_bytes(cipher, 'big', signed=False)
    plain_int = pow(cipher_int, private_exponent, modulus)
    plain = plain_int.to_bytes(max(1, (plain_int.bit_length() + 7) // 8), 'big')
    (out / 'login-rsa-plain.bin').write_bytes(plain)

    printable = ''.join(chr(b) if 32 <= b < 127 else '.' for b in plain)
    log(
        f'LOGIN_HEADER opcode={opcode} payload_len={payload_len} revision={revision} '
        f'sub_revision={sub_revision} client_revision={client_revision} client_type={client_type} '
        f'rsa_len={rsa_len} xtea_body_len={len(packet)-rsa_end}'
    )
    log(f'LOGIN_RSA_PLAIN len={len(plain)} hex={plain.hex()} ascii={printable}')

    if len(plain) < 25 or plain[0] != 1:
        log('LOGIN_RSA_LAYOUT_UNEXPECTED')
        return None

    keys = struct.unpack('>4I', plain[1:17])
    seed = int.from_bytes(plain[17:25], 'big')
    log('LOGIN_XTEA_KEYS ' + ','.join(f'0x{x:08x}' for x in keys))
    log(f'LOGIN_SERVER_SEED 0x{seed:016x}')

    tail_plain = xtea_decrypt(packet[rsa_end:], keys)
    (out / 'login-xtea-plain.bin').write_bytes(tail_plain)
    tail_ascii = ''.join(chr(b) if 32 <= b < 127 else '.' for b in tail_plain)
    username, pos = cstring(tail_plain, 0)
    log(f'LOGIN_XTEA_PLAIN len={len(tail_plain)} hex={tail_plain.hex()} ascii={tail_ascii}')
    log(f'LOGIN_USERNAME value={username!r} next_offset={pos}')
    return keys


def success_metadata():
    # Revision-239 success metadata with tokenFlag == 0:
    # tokenFlag:u8, rights:u8, accountFlag:u8, localPlayerIndex:u16,
    # sessionFlag:u8, then three u64 session/account values.
    meta = bytearray()
    meta += b'\x00'          # token/account hash absent
    meta += b'\x00'          # rights / staff level
    meta += b'\x00'          # account flag
    meta += b'\x00\x01'    # local player index = 1
    meta += b'\x00'          # session/member flag
    meta += b'\x00' * 8
    meta += b'\x00' * 8
    meta += b'\x00' * 8
    assert len(meta) == 30
    return bytes(meta)

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
                        if total >= 3:
                            time.sleep(0.25)
                            c.settimeout(0.5)
                except socket.timeout:
                    pass
                data = b''.join(chunks)
                (out / 'login-after-seed.bin').write_bytes(data)
                log(f'LOGIN_AFTER_SEED len={len(data)} hex={data.hex()}')
                if not data:
                    return
                try:
                    keys = decrypt_login(data, out, log)
                except Exception as e:
                    log(f'LOGIN_DECRYPT_ERROR {type(e).__name__}: {e}')
                    return
                if keys is None:
                    return

                metadata = b'\x02' + bytes([30]) + success_metadata()
                first_world = build_initial_frame(keys, packet_id=2, tile_x=3222, tile_y=3218)
                (out / 'first-world-frame.bin').write_bytes(first_world)
                c.sendall(metadata + first_world)
                log(f'LOGIN_SUCCESS_METADATA_SENT len={len(metadata)} hex={metadata.hex()}')
                log(
                    f'FIRST_WORLD_PACKET_SENT packet_id=2 frame_len={len(first_world)} '
                    f'payload_len={len(first_world)-3} opcode_wire=0x{first_world[0]:02x}'
                )

                # Keep the local game connection open long enough to distinguish
                # client-side EOF/errors from the old probe intentionally closing it.
                deadline = time.time() + 30.0
                c.settimeout(0.25)
                seq = 0
                log('SESSION_HOLD_START seconds=30')
                while time.time() < deadline:
                    try:
                        after = c.recv(65536)
                    except socket.timeout:
                        continue
                    except OSError as e:
                        log(f'SESSION_SOCKET_ERROR {e!r}')
                        break
                    if not after:
                        log('SESSION_PEER_EOF')
                        break
                    seq += 1
                    with (out / 'post-world-client.bin').open('ab') as f:
                        f.write(after)
                    log(f'POST_WORLD_CLIENT seq={seq} len={len(after)} hex={after.hex()}')
                else:
                    log('SESSION_HOLD_COMPLETE')
                return

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
