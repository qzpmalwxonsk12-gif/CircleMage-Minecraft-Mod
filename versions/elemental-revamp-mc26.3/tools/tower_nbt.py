"""Lossless typed NBT reader plus terrain-safe tower air cleanup (V2 folder only)."""
from pathlib import Path
import gzip
import io
import struct
import json
import sys

class Tag:
    def __init__(self, kind, value): self.kind, self.value = kind, value
    def __getitem__(self, key): return self.value[key]

def read(stream, fmt):
    count = struct.calcsize(fmt)
    data = stream.read(count)
    if len(data) != count: raise ValueError('Truncated NBT')
    return struct.unpack(fmt, data)[0]

def string(stream): return stream.read(read(stream, '>H')).decode('utf-8')

def payload(stream, kind):
    if kind in range(1, 7): return Tag(kind, read(stream, {1:'>b',2:'>h',3:'>i',4:'>q',5:'>f',6:'>d'}[kind]))
    if kind == 7: return Tag(kind, stream.read(read(stream, '>i')))
    if kind == 8: return Tag(kind, string(stream))
    if kind == 9:
        element = read(stream, '>b'); count = read(stream, '>i')
        return Tag(kind, (element, [payload(stream, element) for _ in range(count)]))
    if kind == 10:
        values = {}
        while True:
            element = read(stream, '>b')
            if element == 0: break
            name = string(stream); values[name] = payload(stream, element)
        return Tag(kind, values)
    if kind in (11, 12):
        count = read(stream, '>i'); fmt = '>i' if kind == 11 else '>q'
        return Tag(kind, [read(stream, fmt) for _ in range(count)])
    raise ValueError(f'Unsupported NBT type {kind}')

def encode_string(value):
    data = value.encode('utf-8'); return struct.pack('>H', len(data)) + data

def encode(tag):
    k, v = tag.kind, tag.value
    if k in range(1, 7): return struct.pack({1:'>b',2:'>h',3:'>i',4:'>q',5:'>f',6:'>d'}[k], v)
    if k == 7: return struct.pack('>i', len(v)) + v
    if k == 8: return encode_string(v)
    if k == 9: return struct.pack('>bi', v[0], len(v[1])) + b''.join(encode(x) for x in v[1])
    if k == 10: return b''.join(struct.pack('>b',x.kind)+encode_string(n)+encode(x) for n,x in v.items()) + b'\0'
    if k in (11,12): return struct.pack('>i',len(v)) + b''.join(struct.pack('>i' if k==11 else '>q',x) for x in v)
    raise ValueError(k)

def load(path):
    raw = gzip.decompress(path.read_bytes()); stream = io.BytesIO(raw)
    kind = read(stream, '>b'); name = string(stream); root = payload(stream, kind)
    assert stream.read() == b''
    assert struct.pack('>b',kind)+encode_string(name)+encode(root) == raw, 'NBT roundtrip changed content'
    return name, root

def positions(root):
    palette = root['palette'].value[1]
    air = {i for i,t in enumerate(palette) if t['Name'].value in ('minecraft:air','minecraft:cave_air','minecraft:void_air')}
    blocks = root['blocks'].value[1]
    points = [(tuple(t.value for t in b['pos'].value[1]), b, b['state'].value in air) for b in blocks]
    return points

if __name__ == '__main__':
    folder = Path(__file__).resolve().parents[1]
    assert folder == Path('C:/Users/Arjun/OneDrive/Desktop/ModMagicV2'), 'Working copy only'
    reports = []
    for path in (folder/'src/main/resources/data/circlemage/structure').glob('*.nbt'):
        name, root = load(path); points = positions(root)
        solid = [p for p,b,a in points if not a]
        bounds = [(min(p[d] for p in solid),max(p[d] for p in solid)) for d in range(3)]
        columns = {}
        for x,y,z in solid:
            if (x,z) not in columns: columns[x,z] = [y,y]
            else:
                columns[x,z][0] = min(columns[x,z][0],y)
                columns[x,z][1] = max(columns[x,z][1],y)
        # An interior air cell has real tower material both below and above its column.
        # Exterior padding, space above roofs and open broken-tower margins are omitted.
        kept = [b for (x,y,z),b,a in points if not a or ((x,z) in columns and columns[x,z][0] < y < columns[x,z][1])]
        before_solid = [encode(b) for p,b,a in points if not a]
        root['blocks'].value = (10,kept)
        after_solid = [encode(b) for p,b,a in positions(root) if not a]
        assert before_solid == after_solid, 'Structural content changed'
        report = {'template':path.name,'solidBlocks':len(solid),'airBefore':sum(a for p,b,a in points),
                  'interiorAirKept':len(kept)-len(solid),'exteriorAirRemoved':len(points)-len(kept),
                  'size':[t.value for t in root['size'].value[1]],'solidBounds':bounds}
        reports.append(report)
        if '--apply' in sys.argv:
            path.write_bytes(gzip.compress(b'\x0a'+encode_string(name)+encode(root),mtime=0))
            _,check = load(path); assert encode(check) == encode(root)
        print(json.dumps(report))
    if '--apply' in sys.argv:
        (folder/'docs/tower-cleanup.json').write_text(json.dumps(reports,indent=2)+'\n')
