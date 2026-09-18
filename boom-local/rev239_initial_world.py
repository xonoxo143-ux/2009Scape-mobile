#!/usr/bin/env python3
"""Minimal revision-239 server-side framing for the first post-login packet.

This is intentionally tiny: enough ISAAC and bit packing to generate the packet
that the Boom/OSRS client expects immediately after successful login. It is not
a general RuneScape server implementation.
"""
import struct

MASK32 = 0xFFFFFFFF
GOLDEN = 0x9E3779B9


class Isaac:
    def __init__(self, seed):
        self.mem = [0] * 256
        self.results = [0] * 256
        for i, value in enumerate(seed[:256]):
            self.results[i] = value & MASK32
        self.a = self.b = self.c = 0
        self._init()
        self.count = 256

    @staticmethod
    def _mix(x):
        a, b, c, d, e, f, g, h = x
        a ^= (b << 11) & MASK32; d = (d + a) & MASK32; b = (b + c) & MASK32
        b ^= c >> 2;             e = (e + b) & MASK32; c = (c + d) & MASK32
        c ^= (d << 8) & MASK32;  f = (f + c) & MASK32; d = (d + e) & MASK32
        d ^= e >> 16;            g = (g + d) & MASK32; e = (e + f) & MASK32
        e ^= (f << 10) & MASK32; h = (h + e) & MASK32; f = (f + g) & MASK32
        f ^= g >> 4;             a = (a + f) & MASK32; g = (g + h) & MASK32
        g ^= (h << 8) & MASK32;  b = (b + g) & MASK32; h = (h + a) & MASK32
        h ^= a >> 9;             c = (c + h) & MASK32; a = (a + b) & MASK32
        return [v & MASK32 for v in (a, b, c, d, e, f, g, h)]

    def _init(self):
        x = [GOLDEN] * 8
        for _ in range(4):
            x = self._mix(x)
        for i in range(0, 256, 8):
            x = [(x[j] + self.results[i + j]) & MASK32 for j in range(8)]
            x = self._mix(x)
            self.mem[i:i + 8] = x
        for i in range(0, 256, 8):
            x = [(x[j] + self.mem[i + j]) & MASK32 for j in range(8)]
            x = self._mix(x)
            self.mem[i:i + 8] = x
        self._generate()

    def _generate(self):
        self.c = (self.c + 1) & MASK32
        self.b = (self.b + self.c) & MASK32
        for i in range(256):
            x = self.mem[i]
            mode = i & 3
            if mode == 0:
                self.a ^= (self.a << 13) & MASK32
            elif mode == 1:
                self.a ^= self.a >> 6
            elif mode == 2:
                self.a ^= (self.a << 2) & MASK32
            else:
                self.a ^= self.a >> 16
            self.a = (self.a + self.mem[(i + 128) & 255]) & MASK32
            y = (self.mem[(x & 1020) >> 2] + self.a + self.b) & MASK32
            self.mem[i] = y
            self.b = (self.mem[((y >> 8) & 1020) >> 2] + x) & MASK32
            self.results[i] = self.b

    def next_int(self):
        if self.count == 0:
            self._generate()
            self.count = 256
        self.count -= 1
        return self.results[self.count]


class BitWriter:
    def __init__(self):
        self.data = bytearray()
        self.bitpos = 0

    def write(self, value, count):
        for shift in range(count - 1, -1, -1):
            byte_index = self.bitpos >> 3
            bit_index = 7 - (self.bitpos & 7)
            if byte_index == len(self.data):
                self.data.append(0)
            if (value >> shift) & 1:
                self.data[byte_index] |= 1 << bit_index
            self.bitpos += 1

    def bytes(self):
        return bytes(self.data)


def _short_add(value):
    """Inverse of the client's Buffer.H(): high byte, low byte + 128."""
    return bytes([(value >> 8) & 0xFF, ((value & 0xFF) + 128) & 0xFF])


def build_initial_payload(tile_x=3222, tile_y=3218):
    # Client iy.a(cH): 30 bits for the local player, then 18 bits for player
    # slots 1..2047 except Client.Q. With local index 1, that is 2046 remote
    # entries. Slot zero is not part of this bootstrap loop.
    bits = BitWriter()
    packed_local = ((0 & 3) << 28) | ((tile_x & 0x3FFF) << 14) | (tile_y & 0x3FFF)
    bits.write(packed_local, 30)
    for player_index in range(1, 2048):
        if player_index == 1:
            continue
        bits.write(0, 18)
    payload = bytearray(bits.bytes())

    # cB.a(cH) then consumes J(), H(), H(). J() is a signed little-endian
    # short whose value is ignored here. H() is big-endian short-add.
    payload += b'\x00\x00'
    chunk_x = tile_x >> 3
    chunk_y = tile_y >> 3
    payload += _short_add(chunk_x)
    payload += _short_add(chunk_y)
    return bytes(payload)


def build_initial_frame(xtea_keys, packet_id=2, tile_x=3222, tile_y=3218):
    # The login bootstrap uses network packet index 2 (variable u16 length).
    # After reading it, the login state itself consumes the player/bootstrap
    # bits via iy.a(cH) and the initial region data via cB.a(cH).
    cipher = Isaac([((k + 50) & MASK32) for k in xtea_keys])
    encoded_opcode = (packet_id + cipher.next_int()) & 0xFF
    payload = build_initial_payload(tile_x, tile_y)
    return bytes([encoded_opcode]) + struct.pack('>H', len(payload)) + payload


if __name__ == '__main__':
    keys = [0x38A51F5B, 0xC5C1984A, 0x40DB307C, 0xA2D36278]
    c = Isaac([k + 50 for k in keys])
    first = c.next_int()
    second = c.next_int()
    assert first == 0x89C69E78
    frame = build_initial_frame(keys)
    print('selftest=PASS first_isaac=%08x second_isaac=%08x packet_id=2 payload_len=%d frame_len=%d' % (first, second, len(frame) - 3, len(frame)))
