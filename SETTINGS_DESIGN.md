# LT5Lyrics settings inventory and redesign plan

This inventory was written after inspecting the 1.8.0 settings UI, configuration,
normal and Sky lyric renderers, HUD, saved defaults and input/scroll handling,
and **before** implementing the redesigned UI.

## Existing settings inventory

The saved `settings.json` is the authoritative store shared by both views. The
following groups account for the persistent settings and related operations:

- **Studio:** `advancedSettingsMode`, `lastStudioTab`; the remembered category
  belongs to the old, full Studio. The old Simple view was not a separate design.
- **Normal 3D placement/visibility:** `worldText`, `btp`, `throughBlocks`,
  `follow`, `locked`, `normalDistance`, `normalOffset`, `normalSide`,
  `normalCurveEnabled`, `normalCurve`, `normalWordsPerLine`,
  `normalLineSpacing`, `normalTextSize`, `normalHideBehind`.
- **Normal 3D readability:** `hologramBackground`, `hologramBgOpacity`,
  `hologramShadow`, `hologramScale`, `hologramBillboard`.
- **Text/animation:** `fontPreset`, `customFontName`, `lyricAnimation`,
  `animationSpeed`, `lyricSmoothness`, `lyricTextScale`, `lyricLineSpacing`,
  `lyricGlowEnabled`, `lyricGlowIntensity`, `lyricOutlineThickness`,
  `showTranslations`.
- **Music bar:** `showHud`, `posX`, `posY`, `hudOpacity`, `hudBeatPulse`,
  `trackToast`, `visualizerStyle`, `clockMode`, `hideBossbar`.
- **Sky:** `skyLyrics`, `skyThroughBlocks`, `skyCrosshairFollow`,
  `skyBoundaryRadius`, `skyHeight`, `skyTextSize`, `skyDriftSpeed`, `skyGlow`,
  `skyAngleDegrees`, `skyCurve`, `skyContextLines`, `skySongHeader`,
  `skyBeatPulse`. The stable Sky renderer ignores the retained legacy drift
  value; Simple must not claim that it changes drift.
- **Colors:** `lyricPrimaryColor`, `lyricHighlightColor`,
  `lyricTranslationColor`, `lyricGlowColor`, `lyricOutlineColor`,
  `separateGlowColor`, `separateHighlightColor`, `separateSkyColors`,
  `hudActiveColor`, `hudUpcomingColor`, `autoAccentColor`, `hudAccentColor`,
  `hudBgColor`, `skyActiveColor`, `skyGlowColor`.
- **Providers:** `autoPaceUnsyncedLyrics`, `enableLrclib`,
  `enableBetterLyrics`, `enableNetease`, `enableKugou`, `enableLrcmux`,
  `enableMusixmatch`, `enableLyricsOvh`, `providerPriority`,
  `musixmatchApiKey`, `betterLyricsApiKey`.
- **Other data/actions:** per-song offsets and custom lyric files are stored
  separately. Search, preview, before/after, retry, mapper, full reset, per-tab
  resets, keyboard focus, numeric and color input are all existing Studio
  features. Keep the full Studio and these controls in Advanced.

## Design decisions

1. Keep one persistent Simple/Advanced preference. Switching changes the UI
   only, never the last Advanced category, display style or any lyric setting.
   Reopening either host must honor it even when the remembered category is 4.
2. The new Simple view is independent of the old tab content. It has a short
   description, a few large, labeled buttons, and compact **Lyrics / Sky /
   Appearance / Reset** pages. Sky controls are only shown when Sky is selected;
   another page never shows Sky-only switches. The view has its own scrolling
   and no search, color wheel, technical sliders or animated live preview.
3. There was no global lyric-only on/off setting: `worldText` means world
   versus HUD and `showHud` hides the entire music bar. Do not mislabel either.
   To meet the requested on/off control, add a real `lyricsEnabled` preference,
   default true for legacy configs. It gates lyric drawing on both world/Sky
   and HUD surfaces, without disabling music controls, changing the selected
   display style, deleting cached lyrics, or changing behavior when true.
   Expose the same preference in Advanced.
4. **Lyrics:** show/hide lyrics; choose World, Sky, or Top Bar using the
   existing display-mode fields; show translations; for World only, choose
   Left/Right/Alternate for newly placed lyrics. Position uses the existing
   `normalSide` and existing signed-offset helper; no nonexistent Center option.
   The size choices adjust the current World or Sky mode's own size field;
   Top Bar has no real lyric-size setting so no misleading size choice is shown.
5. **Sky:** show only in Sky mode. Follow Look Direction uses the existing yaw-
   only switch; up/down does not alter height. Distance presets use the real
   radius field: Near 120, Medium 360, Far 720 blocks. Also expose previous/
   next lines and song header, as real existing switches. All other Sky
   geometry, height, glow, curve, and advanced controls remain in Advanced.
6. **Appearance:** current World size presets 0.70/1.00/1.50, Sky presets
   0.30/0.50/0.80, named Small/Medium/Large. They modify only the applicable
   mode-specific field; existing Advanced global scale multipliers still
   apply. Color choices use the actual primary lyric color. If separate Sky
   colors were explicitly configured, the Sky choice changes only the existing
   Sky primary; otherwise it changes the shared lyric primary. It never turns
   separate colors off or overwrites separate highlight, glow, HUD or outline
   colors. For Normal 3D only, background choices use the actual plate:
   Off, Soft (0.32), Strong (0.65). Off preserves the stored opacity.
7. Every preset is compared to its backing field(s), using a small floating-
   point tolerance. Values not exactly matching a preset are labeled **Custom**
   without overwriting them merely by opening or switching views. The legacy
   random Normal side is also shown as Custom. There is no Animation Off option
   in the existing animator, so no fake animation switch is added.
8. **Reset Simple Settings** requires confirmation and resets only the fields
   Simple exposes, including conditionally visible World/Sky controls: lyric
   enabled, display-mode flags, translations, Normal side and its offset sign,
   Normal and Sky text size, Sky follow/distance/context/header, primary lyric
   color (and Sky primary only when it is separately used), and Normal 3D
   background and opacity. It keeps the Normal offset magnitude, top bar
   visibility, UI mode, detailed settings, separately configured colors and
   effects, fonts, providers, keys, per-song timing, custom files and caches.
   Reset All remains separately confirmed in Advanced.
9. Both full-screen and embedded hosts use the same shared settings panel.
   Simple pages and Advanced categories each remain scrollable; draw and hit
   testing clip to the viewport. Mode switch, confirmation and keyboard input
   must work at common Minecraft GUI scales.
10. Missing/invalid new preferences default safely, existing config values are
    retained, unknown JSON keys are preserved when saving, and malformed JSON
    is not silently discarded. Minecraft/Fabric versions and every existing
    provider, lyric renderer, timing, effect and animation remain unchanged when
    the new global visibility option is on.


## Later acceptance changes and persistence hardening

- In-world holograms are display-only. The Minecraft mouse mixin no longer
  sends button or scroll events to World/Sky lyrics, and the legacy public
  input methods return false. Music bar and settings interaction is retained.
  This intentionally removes the previous hologram right-click drag.
- On reload, an explicit `separateSkyColors` choice is respected even if the
  dormant Sky color differs from the shared lyric color. Only legacy files
  without the flag infer it from the colors. A saved HUD title color is also
  retained; legacy files without one use the shared lyric color. These rules
  prevent a Simple color choice or Simple reset from changing unrelated colors
  or silently switching Sky back to a separate color after restart.
