#!/usr/bin/env python3
"""Package and sign Mono Launcher without the Android SDK.

Builds an APK from a compiled classes.dex, a text AndroidManifest.xml (encoded
with pyaxml), the prebuilt resources.arsc and drawable, and assets, then signs
it with APK Signature Scheme v2 (enough for minSdk 26).
"""
import argparse, hashlib, struct, zipfile, io, datetime
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa, padding
import pyaxml
from lxml import etree

V2_BLOCK_ID = 0x7109871A
RSA_PKCS1_SHA256 = 0x0103
CHUNK = 1024 * 1024


def lp(b):
    return struct.pack('<I', len(b)) + b


def make_key(path):
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'Mono Launcher')])
    now = datetime.datetime(2026, 10, 1, tzinfo=datetime.timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
            .public_key(key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now).not_valid_after(now + datetime.timedelta(days=365 * 30))
            .sign(key, hashes.SHA256()))
    with open(path, 'wb') as f:
        f.write(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                  serialization.NoEncryption()))
        f.write(cert.public_bytes(serialization.Encoding.PEM))


def load_key(path):
    data = open(path, 'rb').read()
    key = serialization.load_pem_private_key(data, None)
    cert = x509.load_pem_x509_certificate(data[data.index(b'-----BEGIN CERTIFICATE-----'):])
    return key, cert


def encode_manifest(xml_path):
    root = etree.parse(xml_path).getroot()
    axml = pyaxml.AXML()
    axml.from_xml(root)
    return axml.pack()


def build_zip(entries):
    """entries: list of (name, bytes, store). Stored entries are 4-byte aligned."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as z:
        for name, data, store in entries:
            info = zipfile.ZipInfo(name, date_time=(1981, 1, 1, 0, 0, 0))
            info.create_system = 0
            if store:
                info.compress_type = zipfile.ZIP_STORED
                header_end = buf.tell() + 30 + len(name.encode())
                pad = (4 - header_end % 4) % 4
                if pad:
                    total = pad + 4  # an extra record needs at least a 4-byte header
                    info.extra = struct.pack('<HH', 0xD935, total - 4) + b'\x00' * (total - 4)
            else:
                info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    return buf.getvalue()


def find_eocd(apk):
    i = apk.rfind(b'PK\x05\x06')
    if i < 0:
        raise SystemExit('no EOCD')
    cd_size, cd_offset = struct.unpack('<II', apk[i + 12:i + 20])
    return i, cd_offset, cd_size


def chunked_digest(sections):
    digests = []
    for sec in sections:
        for off in range(0, len(sec), CHUNK):
            c = sec[off:off + CHUNK]
            digests.append(hashlib.sha256(b'\xa5' + struct.pack('<I', len(c)) + c).digest())
    return hashlib.sha256(b'\x5a' + struct.pack('<I', len(digests)) + b''.join(digests)).digest()


def sign_v2(apk, key, cert):
    eocd, cd_off, cd_size = find_eocd(apk)
    entries, cd, eocd_bytes = apk[:cd_off], apk[cd_off:eocd], apk[eocd:]
    digest = chunked_digest([entries, cd, eocd_bytes])
    cert_der = cert.public_bytes(serialization.Encoding.DER)
    signed_data = (lp(lp(struct.pack('<I', RSA_PKCS1_SHA256) + lp(digest)))
                   + lp(lp(cert_der))
                   + lp(b''))
    sig = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    pub = key.public_key().public_bytes(serialization.Encoding.DER,
                                        serialization.PublicFormat.SubjectPublicKeyInfo)
    signer = lp(signed_data) + lp(lp(struct.pack('<I', RSA_PKCS1_SHA256) + lp(sig))) + lp(pub)
    value = lp(lp(signer))
    pair = struct.pack('<Q', 4 + len(value)) + struct.pack('<I', V2_BLOCK_ID) + value
    size = len(pair) + 8 + 16
    block = struct.pack('<Q', size) + pair + struct.pack('<Q', size) + b'APK Sig Block 42'
    new_eocd = eocd_bytes[:16] + struct.pack('<I', cd_off + len(block)) + eocd_bytes[20:]
    return entries + block + cd + new_eocd


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--manifest', required=True)
    ap.add_argument('--dex', required=True)
    ap.add_argument('--arsc', required=True)
    ap.add_argument('--res', nargs='*', default=[], help='zip_path=file pairs')
    ap.add_argument('--asset', nargs='*', default=[], help='zip_path=file pairs')
    ap.add_argument('--key', required=True)
    ap.add_argument('--new-key', action='store_true')
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    if a.new_key:
        make_key(a.key)
    key, cert = load_key(a.key)
    entries = [('AndroidManifest.xml', encode_manifest(a.manifest), False)]
    for pair in a.res:
        name, path = pair.split('=', 1)
        entries.append((name, open(path, 'rb').read(), False))
    entries.append(('resources.arsc', open(a.arsc, 'rb').read(), True))
    for pair in a.asset:
        name, path = pair.split('=', 1)
        entries.append((name, open(path, 'rb').read(), False))
    entries.append(('classes.dex', open(a.dex, 'rb').read(), False))
    apk = sign_v2(build_zip(entries), key, cert)
    open(a.out, 'wb').write(apk)
    print('wrote', a.out, len(apk), 'bytes; cert sha256',
          cert.fingerprint(hashes.SHA256()).hex())


if __name__ == '__main__':
    main()
