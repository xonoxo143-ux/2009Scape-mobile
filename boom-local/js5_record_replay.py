#!/usr/bin/env python3
import argparse, base64, json, os, select, socket, time
from pathlib import Path

HOST='127.0.0.1'; PORT=43594
REMOTE_HOST='world1.boom-ps.com'; REMOTE_PORT=43594


def recv_exact(sock, n):
    out=bytearray()
    while len(out)<n:
        b=sock.recv(n-len(out))
        if not b: raise EOFError(f'eof after {len(out)}/{n}')
        out.extend(b)
    return bytes(out)


def record(path):
    events=[]
    with socket.socket() as ls:
        ls.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        ls.bind((HOST,PORT)); ls.listen(1)
        print(f'RECORD listening {HOST}:{PORT}', flush=True)
        client,addr=ls.accept()
        print(f'RECORD client {addr}', flush=True)
        with client, socket.create_connection((REMOTE_HOST,REMOTE_PORT),10) as remote:
            client.setblocking(False); remote.setblocking(False)
            while True:
                ready,_,_=select.select([client,remote],[],[],30)
                if not ready: break
                for src in ready:
                    direction='C>S' if src is client else 'S>C'
                    dst=remote if src is client else client
                    try: data=src.recv(65536)
                    except BlockingIOError: continue
                    if not data:
                        path.write_text('\n'.join(json.dumps(e) for e in events)+'\n')
                        print(f'RECORD eof events={len(events)}',flush=True); return
                    events.append({'d':direction,'b':base64.b64encode(data).decode()})
                    try: dst.sendall(data)
                    except (BrokenPipeError,OSError):
                        path.write_text('\n'.join(json.dumps(e) for e in events)+'\n'); return
    path.write_text('\n'.join(json.dumps(e) for e in events)+'\n')
    print(f'RECORD done events={len(events)} bytes={path.stat().st_size}',flush=True)


def replay(path):
    events=[json.loads(x) for x in path.read_text().splitlines() if x.strip()]
    with socket.socket() as ls:
        ls.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        ls.bind((HOST,PORT)); ls.listen(1)
        print(f'REPLAY listening {HOST}:{PORT} events={len(events)}',flush=True)
        client,addr=ls.accept()
        print(f'REPLAY client {addr}',flush=True)
        with client:
            client.settimeout(20)
            pending=bytearray()
            for i,e in enumerate(events):
                data=base64.b64decode(e['b'])
                if e['d']=='S>C':
                    client.sendall(data)
                    continue
                while len(pending)<len(data):
                    chunk=client.recv(max(4096,len(data)-len(pending)))
                    if not chunk: raise EOFError(f'client eof at event {i}')
                    pending.extend(chunk)
                got=bytes(pending[:len(data)]); del pending[:len(data)]
                if got!=data:
                    print(f'MISMATCH event={i} expected={data[:64].hex()} got={got[:64].hex()}',flush=True)
                    # Stop immediately: sending later responses against a divergent request stream is invalid.
                    return 3
            print('REPLAY completed event stream',flush=True)
    return 0


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('mode',choices=['record','replay']); ap.add_argument('trace',type=Path)
    a=ap.parse_args(); a.trace.parent.mkdir(parents=True,exist_ok=True)
    if a.mode=='record': record(a.trace); return 0
    return replay(a.trace)

if __name__=='__main__': raise SystemExit(main())
