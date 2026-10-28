# Music Island picture and Normal 3D follow investigation

Inspection was performed on the complete, SHA-256-verified 1.9.0 source that was previously delivered, before this change. The active workspace contained an incomplete older snapshot, so the delivered source archive was restored before implementation.

## Music Island and asset

* `MusicIsland.render()` draws the compact pill and expanded card inside a scissor. With a media session, `drawExpanded()` draws **Share at the top-right**, not the top-left. The user selected **keep Share where it is and put the supplied picture directly below it**. The image is the supplied 32×32 PNG, not a substitute.
* The album cover occupies the top-left; the song title and artist occupy the header, with the playback progress bar below. The new picture needs a reserved area under Share, so the artist's header must be clipped before that area. The image should be drawn only while the card is expanded, including the no-session state, and use a cached Minecraft resource texture with 50% alpha. Neither the compact pill nor full Studio/unrelated screens should show it.

## Normal 3D position and follow

* `spawnNormalNode()` computes one anchor from the partial-tick interpolated player eye, current view, configured distance, side/offset and a small vertical group offset, with a best-effort solid-block check. It stores the per-group distance, side and offset on `Placed`.
* `render()` creates a new group when the timed lyric index changes and retires the old group. With Follow disabled, no normal position update should run; camera-facing orientation must not write to `anchor`. Existing `normalHideBehind` is a draw-time visibility option, not a line-lifetime or placement update, and must remain compatible. As usual, text outside the camera frustum or behind terrain cannot be seen.
* The actual fast-movement failure is in `follow()`: before it calculates a new target, it returns if the current anchor is behind the camera and `normalHideBehind` is enabled (the default). Once fast movement or a turn puts a group behind, its update is permanently skipped until the next lyric group is spawned. Additionally, smoothing the **absolute** world anchor toward a moving target introduces player-translation lag; the frame delta is capped, making lag worse after a slow frame.
* Correct Follow by updating every active/retiring Normal group every render regardless of current visibility, using the same partial-tick eye and camera view as initial placement. Transport the group by actual eye displacement and smooth the remaining player-relative offset/orientation; this tracks translation without accumulating a per-frame offset and avoids requiring a new lyric. Preserve per-group distance, signed offset and height. Do not alter Sky placement.

## Normal 3D orientation and timing

* `drawHologram()` builds all wrapped rows and translations into one group and submits real world-space glyphs about `placed.anchor`. The default Natural 3D billboard currently returns the spawn-time basis, so a fixed lyric does not turn toward a moving camera. Face Camera and Upright are existing saved choices and should remain selectable.
* For Normal groups, compute a camera-facing basis at draw time, preserve upright text, group row alignment and curvature, and never change the stored anchor during rotation. Keep the existing alternate billboard choices; no shared Sky rendering changes. In fixed mode, the group origin remains at its anchor rather than applying a position-changing group lift; fade/scale and per-glyph effects remain.
* The current lyric is selected by timestamps; a new timestamp or a blank line retires it and the existing exit animation eventually removes it. The last line has no separate end timestamp in ordinary LRC; when a real media duration is known, retire it at the actual track end. When duration is unknown, do not invent an end time. Preserve seeks, lyric offsets, translations and karaoke timing.

## In-game refinement

An initial client turn test revealed that generic 3D quaternion interpolation took one 180-degree turn over the player's head. Normal Follow now interpolates its horizontal angle, horizontal radius and vertical offset separately, and caps a single frame's correction. It still transports the anchor by actual eye movement before smoothing the relative offset. A separate bounded Normal Follow delta allows recovery at low frame rates while leaving the existing animation and Sky delta unchanged.
