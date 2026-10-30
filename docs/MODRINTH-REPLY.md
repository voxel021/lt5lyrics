# Moderator reply — template, not ready to submit

Do not post a claim of full resolution until the media DLL origin is verified and the tags/releases actually exist publicly. The original LT5Lyrics/Song Island source URL is also still needed for accurate upstream credit.

---

Hello, thank you for the review. I maintain the VoxelZero-modified LT5Lyrics release.

Repository: https://github.com/voxel021/lt5lyrics

The native inventory and evidence are documented in `NATIVES.md` in that repository. The two submitted JARs were audited recursively, including embedded dependency JARs:

| Minecraft / mod | Native files | Origin |
| --- | --- | --- |
| 1.21.11 / 1.11.7 | 28 JNA natives and `MediaPlayerInfo.dll` | JNA 5.15.0 payloads verified against upstream tag; media DLL origin still being investigated |
| 26.2 / 1.11.7+mc26.2 | 28 JNA natives and `MediaPlayerInfo.dll` | Same payloads and investigation status |

JNA source tag: https://github.com/java-native-access/jna/tree/5.15.0
Commit: `fad4d1dc4e791f8083d6a3afa28a3ea8bdcbe0ee`.
The full table records every native path and its SHA-256 evidence. Neither audited version is native-free. Original credits and third-party licenses are retained.

[Before submission: replace this paragraph with the media DLL's verified origin URL and exact release/commit. Do not claim that the unresolved candidate origin is verified.]

[Before submission: add the actual public per-version source tag and release links for Minecraft 1.21.11 and 26.2. Proposed names are `mc1.21.11-v1.11.7` and `mc26.2-v1.11.7`; they have not been created by this change.]

[Only after builds succeed and the exact artifacts are uploaded: state that the replacement Modrinth JARs are the exact GitHub Actions release artifacts, and include their SHA-256 checksums. Do not claim the original submitted JARs match without comparison.]

Thank you for your time.
