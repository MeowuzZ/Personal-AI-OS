# Public Android signing key

This directory intentionally contains the signing material used for Personal AI OS releases.

## Files

- `personal-ai-os-release.keystore`: PKCS#12 keystore containing the release private key.
- `password.txt`: public keystore and key password.

The key alias is `personal-ai-os`.

Certificate SHA-256 fingerprint:

```text
E0:89:0E:FA:44:BD:45:92:3C:14:6B:2E:7C:52:B5:3F:33:BE:33:97:61:8D:DB:50:D3:4A:DF:CC:6A:73:4F:D4
```

## Security model

The signing key and password are public by the repository owner's explicit choice so they can be recovered from Git history and are not dependent on one computer or GitHub Secret. This means anyone with repository access can sign an APK that Android considers to have the same application identity. Do not treat this certificate as proof that an APK was produced by the repository owner; download releases from the official repository and verify the published SHA-256 checksum.

Replacing this key prevents existing installations from accepting future APKs as updates. Keep it unchanged unless the application ID or migration strategy also changes.
