# Native binary provenance

> **Public distribution origin verified:** the media DLL matches Song Island at the commit linked below.
> Its ultimate MediaPlayerInfo compiler source/release is not independently established.
> A successful Gradle build copies that DLL; it does not rebuild or authenticate it.

## Scope

Audited the two supplied release JARs, including every nested JAR. Each contains **29 native files**. Neither audited version is native-free. No claim is made about other uploads or versions. The source ZIPs for both versions contain the same media DLL hash as the submitted JARs.

Both builds declare `net.java.dev.jna:jna:5.15.0`. All 28 native payloads from each nested JNA JAR were compared against the corresponding `lib/native/<platform>.jar` in the upstream source tag and **all 56 comparisons matched byte-for-byte**.

- JNA repository/tag: https://github.com/java-native-access/jna/tree/5.15.0
- Exact JNA tag commit: `fad4d1dc4e791f8083d6a3afa28a3ea8bdcbe0ee`. This establishes matching distributed native payloads, not independent reproduction of upstream compiler output.
- [Full per-file SHA-256 inventory](docs/native-audit/submitted-jars.json)
- [JNA upstream comparison evidence](docs/native-audit/jna-upstream-comparison.json)

## Submitted artifacts

| Minecraft | Mod version | Submitted JAR | SHA-256 |
| --- | --- | --- | --- |
| 1.21.11 | 1.11.7 | `lt5lyrics-1.11.7.jar` | `78f4360133af11f33f41b6fc5c253bf558c94247fc03547a08c25cb05db8b43e` |
| 26.2 | 1.11.7+mc26.2 | `lt5lyrics-1.11.7+mc26.2.jar` | `8cef5e1d62cabc7e67c5fc3c571898bab5f999c147ab231d1e860c0edd0f650c` |

## Native inventory

Each row applies to **both** listed versions; full paths and individual hashes are in the JSON inventory. `!/` denotes a file inside a nested JAR.

| Version (Minecraft) | Native file | Third-party or own | Origin link | Exact version/tag/commit |
| --- | --- | --- | --- | --- |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/darwin-aarch64/libjnidispatch.jnilib` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/darwin-aarch64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/darwin-x86-64/libjnidispatch.jnilib` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/darwin-x86-64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/dragonflybsd-x86-64/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/dragonflybsd-x86-64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/freebsd-aarch64/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/freebsd-aarch64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/freebsd-x86-64/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/freebsd-x86-64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/freebsd-x86/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/freebsd-x86.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/linux-aarch64/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/linux-aarch64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/linux-arm/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/linux-arm.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/linux-armel/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/linux-armel.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/linux-loongarch64/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/linux-loongarch64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/linux-mips64el/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/linux-mips64el.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/linux-ppc/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/linux-ppc.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/linux-ppc64le/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/linux-ppc64le.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/linux-riscv64/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/linux-riscv64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/linux-s390x/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/linux-s390x.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/linux-x86-64/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/linux-x86-64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/linux-x86/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/linux-x86.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/openbsd-x86-64/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/openbsd-x86-64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/openbsd-x86/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/openbsd-x86.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/sunos-sparc/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/sunos-sparc.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/sunos-sparcv9/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/sunos-sparcv9.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/sunos-x86-64/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/sunos-x86-64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/sunos-x86/libjnidispatch.so` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/sunos-x86.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/win32-aarch64/jnidispatch.dll` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/win32-aarch64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/win32-x86-64/jnidispatch.dll` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/win32-x86-64.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/win32-x86/jnidispatch.dll` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/win32-x86.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `mediaplayerinfo/natives/win/MediaPlayerInfo.dll` | Third-party, unchanged Song Island DLL (verified payload match) | [Song Island vendored DLL](https://github.com/Mimitokox/song-island-minecraft/blob/af87dc41b916bf6f6a95604611efbe8b2d9ff2c4/src/main/resources/mediaplayerinfo/natives/win/MediaPlayerInfo.dll) | Song Island commit `af87dc41b916bf6f6a95604611efbe8b2d9ff2c4`; ultimate MediaPlayerInfo release unknown |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/aix-ppc/libjnidispatch.a` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/aix-ppc.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/aix-ppc64/libjnidispatch.a` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/aix-ppc64.jar) | 5.15.0; commit above |



## MediaPlayerInfo DLL: verified distribution origin

The maintainer identified [Song Island](https://github.com/Mimitokox/song-island-minecraft) as the original project.
Its `src/main/resources/mediaplayerinfo/natives/win/MediaPlayerInfo.dll` at commit [`af87dc41b916bf6f6a95604611efbe8b2d9ff2c4`](https://github.com/Mimitokox/song-island-minecraft/tree/af87dc41b916bf6f6a95604611efbe8b2d9ff2c4) is **byte-for-byte identical** to the media DLL in both submitted LT5Lyrics JARs and both supplied source ZIPs.

- [Permanent upstream binary link](https://github.com/Mimitokox/song-island-minecraft/blob/af87dc41b916bf6f6a95604611efbe8b2d9ff2c4/src/main/resources/mediaplayerinfo/natives/win/MediaPlayerInfo.dll)
- [Comparison evidence](docs/native-audit/song-island-upstream-comparison.json)
- SHA-256: `301c16eeb8223bb26eb31f83c93e8dcf8ddd13625cdd52f05fcd0862ecb858e9`

This establishes an exact public **third-party distribution origin**, not a claim that VoxelZero compiled this DLL. The project already credits [MediaPlayerInfo](https://github.com/Redstonecrafter0/MediaPlayerInfo); that repository is the credited underlying library, but its exact release/native-source commit has **not** been determined. Do not label this DLL as MediaPlayerInfo v0.1.0 without matching evidence. Matching a public binary is not a security review or an independent rebuild.

If moderators require the ultimate native compilation source rather than the supplied equivalent origin, ask the Song Island maintainer which MediaPlayerInfo release or C++ commit/toolchain produced this hash. If any native changes are made, provide their exact source and a Windows compile job, and make Gradle consume that output. No guessed compile step or native replacement has been added.

All existing credits and licenses are preserved: Song Island/MediaPlayerInfo are credited under AGPL-3.0; JNA retains its upstream Apache-2.0/LGPL-2.1 licensing.

## Repeat the audit

Quick top-level listing (does not cover nested JARs):

```sh
unzip -l mod.jar | grep -Ei "\.(dll|so([.][0-9]+)*|dylib|exe|jnilib)$"
```

Complete recursive inventory with SHA-256 (Python 3, no additional packages):

```sh
python tools/audit_natives.py lt5lyrics-1.11.7.jar lt5lyrics-1.11.7+mc26.2.jar
```

The scanner uses filename extensions; it is an inventory, not a malware scanner or proof that a disguised executable is absent. It also checks `.jnilib`, versioned `.so` names, and `.a` archives (including the two JNA AIX native archives).

## Builds and moderation

See [release instructions](docs/RELEASING.md). Each tag selects the matching uploaded source archive, not the root 26.3 project. CI audits its newly built JAR and attaches that report and checksums to a **draft** GitHub release. Drafts must be deliberately published after reviewing provenance and successful builds. Upload the exact resulting installable JAR to Modrinth. Building Java does not prove an unexplained prebuilt native came from source, and a new JAR is not claimed to match an old submitted JAR unless its whole-file hash matches.
