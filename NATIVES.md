# Native binary provenance

> **Not ready to claim full provenance:** the MediaPlayerInfo DLL origin is still unverified.
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
| 1.21.11; 26.2 | `mediaplayerinfo/natives/win/MediaPlayerInfo.dll` | Unverified; appears to be vendored third-party MediaPlayerInfo | [Candidate upstream only](https://github.com/Redstonecrafter0/MediaPlayerInfo) | **Unknown — no release/commit match established** |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/aix-ppc/libjnidispatch.a` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/aix-ppc.jar) | 5.15.0; commit above |
| 1.21.11; 26.2 | `META-INF/jars/jna-5.15.0.jar!/com/sun/jna/aix-ppc64/libjnidispatch.a` | Third-party, verified payload match | [JNA platform archive](https://github.com/java-native-access/jna/blob/5.15.0/lib/native/aix-ppc64.jar) | 5.15.0; commit above |



## Unresolved MediaPlayerInfo DLL

`mediaplayerinfo/natives/win/MediaPlayerInfo.dll` has SHA-256:

```text
301c16eeb8223bb26eb31f83c93e8dcf8ddd13625cdd52f05fcd0862ecb858e9
```

The existing project credits MediaPlayerInfo and contains Java code in `dev.redstones.mediaplayerinfo` that loads this path. Those are clues, not proof of a release. The candidate upstream has C++ sources (`native/main.cpp`, `native/main.h`) and a `v0.1.0` release, but **we have not established that this DLL came from that release**. The upstream release asset could not be downloaded in this workspace, so no claim of a match is made.

To resolve:

1. Ask the maintainer where the DLL was obtained and whether it was modified or compiled locally. Also obtain the original LT5Lyrics/Song Island repository URL; it has not been supplied.
2. Download candidate official release JARs, extract their media DLLs, and compare SHA-256 with the hash above. Record a match with the exact release asset and source tag/commit. Never assign a version just because it is the latest release.
3. If it was modified or built locally, obtain the exact C++ sources and toolchain/build instructions. Add a Windows native-build job, consume its output in Gradle, and test the replacement without changing functionality. Do not claim the candidate upstream sources reproduce this binary without verification.
4. Preserve original license and attribution notices. JNA retains its upstream Apache-2.0/LGPL-2.1 licensing; MediaPlayerInfo is credited under AGPL-3.0, but confirming its exact source remains necessary.

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

See [release instructions](docs/RELEASING.md). Each tag selects the matching uploaded source archive, not the root 26.3 project. CI audits its newly built JAR and attaches that report and checksums to a **draft** GitHub release. Drafts must be deliberately published once provenance is resolved. Upload the exact resulting installable JAR to Modrinth. Building Java does not prove an unexplained prebuilt native came from source, and a new JAR is not claimed to match an old submitted JAR unless its whole-file hash matches.
