# Moderator reply — template, not ready to submit

The third-party distribution origins are verified. Publish the documentation first. Only include replacement-release claims and tag links once those releases actually exist; moderation acceptance is not guaranteed.

---

Hello, thank you for the review. I maintain the VoxelZero-modified LT5Lyrics release.

Repository: https://github.com/voxel021/lt5lyrics

The native inventory and evidence are documented in `NATIVES.md` in that repository. The two submitted JARs were audited recursively, including embedded dependency JARs:

| Minecraft / mod | Native files | Origin |
| --- | --- | --- |
| 1.21.11 / 1.11.7 | 28 JNA natives and `MediaPlayerInfo.dll` | JNA 5.15.0 verified; DLL matches Song Island commit listed below |
| 26.2 / 1.11.7+mc26.2 | 28 JNA natives and `MediaPlayerInfo.dll` | Same verified third-party payloads |

JNA source tag: https://github.com/java-native-access/jna/tree/5.15.0
Commit: `fad4d1dc4e791f8083d6a3afa28a3ea8bdcbe0ee`.
The full table records every native path and its SHA-256 evidence. Neither audited version is native-free. Original credits and third-party licenses are retained.

The media DLL is an unchanged third-party file from the original Song Island project:
https://github.com/Mimitokox/song-island-minecraft/blob/af87dc41b916bf6f6a95604611efbe8b2d9ff2c4/src/main/resources/mediaplayerinfo/natives/win/MediaPlayerInfo.dll

Exact Song Island commit: `af87dc41b916bf6f6a95604611efbe8b2d9ff2c4`.
SHA-256: `301c16eeb8223bb26eb31f83c93e8dcf8ddd13625cdd52f05fcd0862ecb858e9`.
It matches both submitted JARs and both supplied source archives byte-for-byte. This identifies its public distribution origin, not an independently verified native-source rebuild or a particular MediaPlayerInfo release. Please let me know if you require further upstream compilation provenance beyond this equivalent origin.

[Before submission: add the actual public per-version source tag and release links for Minecraft 1.21.11 and 26.2. Proposed names are `mc1.21.11-v1.11.7` and `mc26.2-v1.11.7`; they have not been created by this change.]

[Only after builds succeed and the exact artifacts are uploaded: state that the replacement Modrinth JARs are the exact GitHub Actions release artifacts, and include their SHA-256 checksums. Do not claim the original submitted JARs match without comparison.]

Thank you for your time.
