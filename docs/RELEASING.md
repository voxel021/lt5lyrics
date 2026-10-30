# Version source layout and releases

## Layout

The repository root remains the existing **26.3** project. Do not label it as 1.21.11 or 26.2.

The supplied version-specific sources are retained without rewriting their contents:

| Minecraft | Source archive | JDK | Loom declared by supplied source |
| --- | --- | --- | --- |
| 1.21.11 | `LT5Lyrics-1.11.7-source.zip` | 21 | `1.17-SNAPSHOT` |
| 26.2 | `LT5Lyrics-1.11.7-mc26.2-source.zip` | 25 | `1.17.21` |

`tools/version-sources.json` pins archive SHA-256s. CI extracts the correct archive and verifies its Minecraft version. The historical JARs stay as audit evidence, not as files to pass off as newly built outputs. No Java/native functionality or existing credits were changed.

The older source uses a **mutable Loom snapshot**. This remains unchanged rather than guessing a replacement. This can prevent reproducible future builds. Before claiming deterministic rebuilds, establish and pin the actual resolved Loom artifact/version and verify it builds. Other upstream dependencies/toolchains and ZIP timestamps can also affect reproducibility. CI provenance and a matching artifact hash are not the same as independent bit-for-bit reproducibility.

## Local build

Install the JDK for your target, then run from this repository:

```sh
python tools/extract_version_source.py 1.21.11 /path/to/new-mc12111-folder
cd /path/to/new-mc12111-folder
./gradlew clean build
```

For 26.2, use `26.2` and JDK 25 instead. The destination must not exist. On Windows use `gradlew.bat`.

## Tagging (maintainer instructions, not executed by Arena)

A branch per Minecraft target is optional. This repository instead keeps both source archives in one commit and uses **version-prefixed tags** to select the matching source. A tag links to that complete snapshot; the build does not use the root project for these targets.

After the changes have been reviewed and merged, run the following on your own computer, from an up-to-date clone of this repository. Keep your working tree clean. If either tag exists, do not overwrite it: choose a new release identifier and review what that tag should represent.

```sh
# Update your current checked-out branch's view of the published main commit.
git fetch origin main

# Both tags point explicitly to the reviewed public source snapshot on main.
git tag -a mc1.21.11-v1.11.7 origin/main -m "LT5Lyrics 1.11.7 for Minecraft 1.21.11"
git tag -a mc26.2-v1.11.7 origin/main -m "LT5Lyrics 1.11.7 for Minecraft 26.2"
git push origin refs/tags/mc1.21.11-v1.11.7 refs/tags/mc26.2-v1.11.7
```

These tag names are **proposed, not created or verified existing links**. The tag-triggered workflow builds just the version selected by each tag with the correct JDK. Branch/PR/manual runs check both archived source projects, plus the existing root project's normal build on non-tag runs.

## Before publishing

1. Resolve the media DLL's provenance as described in [NATIVES.md](../NATIVES.md). The workflow does **not** compile the DLL because its exact source and toolchain have not been established. No made-up native compile step is included.
2. Review successful CI logs, the new native inventory, source identity, and the draft release. CI may expose dependency/build problems; none of these version builds has been run locally in this Java-less workspace.
3. Compare new output against historical JAR hashes if asserting equality. Do not assume they are equal. Updated source metadata or dependency resolution can change a JAR's hash.
4. Publish the draft release only when ready and all attribution/license requirements are met. Draft release links/assets are not public to moderators.
5. Download its installable JAR (not `-sources.jar`) and upload **those exact bytes** to Modrinth. Check `SHA256SUMS` before upload.
6. Use the actual published tag/release URLs in the moderator reply; do not send the template's proposed tags as if they existed.

If the DLL is your own modified code, add its exact sources and a Windows compile job first, make Gradle consume that compiled artifact, and validate functionality. Republishing a copied DLL in a Java build does not satisfy the own-native-source requirement.
