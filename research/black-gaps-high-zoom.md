# Why the treemap background is black at high zoom

Research ticket #4 (part of map #3). Read-only investigation; no app code was changed.

## Direct answer

At high zoom the tiles are pushed off-screen by the zoom/pan uniforms, so part of the
viewport shows the cleared GL surface rather than a tile. The GL renderer's
`onDrawFrame` clears the entire surface to **transparent black** (`glClearColor(0, 0, 0, 0)`
+ `glClear`) on every frame. Because the `GLSurfaceView` is written to a separate
surface with `PixelFormat.TRANSLUCENT` and `setZOrderMediaOverlay(true)`, that
transparent-black clear blends onto **black** at the compositor, covering the Compose
`Box` background drawn underneath it. So the black gap is painted by the **GL
surface's clear**, not by Compose.

To show the theme seed colour, the **Compose layer must change**: the seed colour must
be drawn on a Compose surface that sits *above* the GL surface, or the GL surface must
be made to let the Compose background show through. The OpenGL renderer and the
squarified layout stay unchanged.

## The rendering stack and which layer paints the gap

`TreemapCanvas` (`app/src/main/java/com/kd/anddirstat/treemap/TreemapCanvas.kt`) builds
one `Box` containing, bottom to top:

1. `Box(modifier = modifier.fillMaxSize().background(canvasBg))` — line 506. This is
   the base Compose background; `canvasBg` is `surfaceColor` (or `Color.Black` in
   pure-black mode), lines 286-287.
2. `AndroidView` hosting the `GLSurfaceView` — lines 507-530. Crucially it is
   configured (lines 509-516):
   - `setEGLConfigChooser(8, 8, 8, 8, 16, 0)` — line 511 (8 bits of alpha requested),
   - `holder.setFormat(PixelFormat.TRANSLUCENT)` — line 512,
   - `setZOrderMediaOverlay(true)` — line 513,
   - `setRenderer(glRenderer)` / `RENDERMODE_WHEN_DIRTY` — lines 514-515.
3. Compose overlay `Canvas` layers (ambient blobs, app icons, selection outlines,
   status/nav scrims) — lines 532-716.

A `SurfaceView`/`GLSurfaceView` renders into its own compositor layer. By default that
layer is placed behind the window's UI layer, but `setZOrderMediaOverlay(true)` puts
it *above* the window's normal UI layer (while still below other, higher-priority
media layers). That is exactly what the source documentation says: "the surface view's
surface is placed on top of another regular surface view in the window (but still
behind the window itself)" for `setZOrderMediaOverlay`, versus `setZOrderOnTop` which
covers the window's own content. In this layout the GL surface therefore covers the
base Compose `Box` background at line 506. The other Compose layers at lines 532-716
are drawn by the window UI layer and appear on top of the GL surface; the base
background at line 506 does not.

Primary source: AOSP `frameworks/base/core/java/android/view/SurfaceView.java`,
`setZOrderMediaOverlay` / `setZOrderOnTop` and class docs ("The surface is Z ordered so
that it is behind the window holding its SurfaceView; the SurfaceView punches a hole in
its window to allow its surface to be displayed"); Android source docs
`source.android.com/docs/core/graphics/arch-sv-glsv` ("By default, the framework places
the newly created surface behind the app UI surface. You can override the default
Z-ordering to put the new surface on top.").

## Why the uncovered area is black

`TreemapGLRenderer` (`app/src/main/java/com/kd/anddirstat/treemap/TreemapGLRenderer.kt`):

- `onDrawFrame` clears the whole surface each frame: `GLES30.glClearColor(clearR,
  clearG, clearB, clearA)` then `GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)` — lines
  320-321.
- `TreemapCanvas` explicitly forces those clear channels to **0,0,0,0** in
  `LaunchedEffect`: `glRenderer.setClearColor(0f, 0f, 0f, 0f)` — line 290.
  `setClearColor` just stores the four floats — renderer lines 151-156.
- Tiles are only drawn where their rectangles land; `glDrawArraysInstanced` runs after
  the clear (line 333). Where no tile covers a pixel, the cleared colour remains.
- `canvasBg` (line 287) is never passed to `setClearColor`; the clear is hardcoded
  transparent black regardless of theme.

Two compounding facts make that transparent black *look* black:

1. SurfaceView with `PixelFormat.TRANSLUCENT` has an alpha-bearing surface, but
   `setZOrderMediaOverlay(true)` places it above the window's UI layer. Per the AOSP
   docs, with a Z-above-window surface the alpha is applied directly to the surface,
   not to the punched hole; the window content (including the Compose background at
   line 506) is not reliably composited underneath a Z-above surface. The visible
   result is the clear colour (black) against a black backdrop, not the Compose
   `canvasBg`.
2. The activity's theme `windowBackground` is a near-black colour in night mode
   (`app/src/main/res/values-night/themes.xml` line 4: `#111318`; day mode
   `app/src/main/res/values/themes.xml` line 4: `#F1F3F9`), so even where the window
   shows through it is not the seed colour.

## Why high zoom exposes it

Zoom/pan are GPU uniforms, not CPU-recomputed tile geometry. In the vertex shader,
`screenPos = pixelPos * uScale + uOffset` and is mapped to NDC
(`TreemapGLRenderer.kt` lines 45-51); `uScale`/`uOffset` are set from `scale`/`offset`
in `onDrawFrame` (lines 326-327) via `setScaleAndOffset` (lines 141-145).

- Zoom (`scale`) grows to as much as `30f` in the gesture handler
  (`TreemapCanvas.kt` line 396); `offset` is clamped to
  `[viewW - viewW*scale, 0]` / `[viewH - viewH*scale, 0]` (lines 411-421).
- Panning a zoomed treemap moves tiled content off-screen. Any viewport pixel with no
  tile after this transform keeps the GL clear colour from lines 320-321.
- The treemap is also laid out only inside insets (`leftSpacePx`/`topSpacePx`/
  `rightSpacePx`/`bottomSpacePx`, lines 111-137), so margins exist even at scale 1;
  zoom makes the uncovered region much larger.

This is why the effect is specific to high zoom: more of the frame is cleared-but-untiled.

## Which layer must change (Compose-only)

The renderer must stay unchanged. The fix is entirely in Compose, in
`TreemapCanvas.kt`, and must address the layering, not just the colour value:

1. **Paint the seed colour on a Compose layer drawn above the GL surface.**
   The existing overlay `Canvas`/`Box` children (lines 532-716) are composed by the
   window UI layer and already appear above the `GLSurfaceView` — the app icons and
   selection outlines drawn at lines 620-676 are visible over the tiles, which proves
   this layer stacks above the GL surface. A full-size Compose `Box`/`Canvas` placed
   after the `AndroidView` (i.e. one of these overlay children), filled with the theme
   seed colour, will cover the black clear everywhere no tile is drawn. Because the
   overlay is drawn on top, it will also cover tiles unless ordered/clipped so that
   tiles remain visible — so for this to work the seed-colour layer must be **behind**
   the tile-drawing GL content in final visual order.

2. **Make the GL surface let the Compose background show through**, so the existing
   `Box(...background(canvasBg))` at line 506 becomes visible instead of being covered.
   Concretely, change the `AndroidView` setup (lines 509-516): use
   `holder.setFormat(PixelFormat.TRANSPARENT)` and `setZOrderOnTop(false)` /
   `setZOrderMediaOverlay(false)` (default, behind the window) so the GL layer sits
   behind the Compose UI layer and the transparent clear (alpha 0 from line 290)
   composites onto the Compose background. Then set `canvasBg` to the theme seed
   colour. With the surface behind the window, AOSP punches a hole and composites the
   window content underneath the translucent surface, which is the supported path for
   a transparent GL background.

Option 2 is the correct minimal architecture: the GL clear is already transparent
(line 290), so once the surface is behind the window the existing Compose background
at line 506 shows through, and only its colour value needs to become the seed colour.
Changing the renderer's clear colour is explicitly out of scope and would also tint the
interior of tiles that are not fully opaque.

Note the map (#3) also removes the ambient-blob layer (lines 221-283 and 532-609),
which currently colours the margins with derived tile colours. Once those blobs are
gone, the only background in the margins is this Compose layer, which is why getting
the layering right matters.

## The theme seed colour symbol and its source

"Theme seed colour" is defined in `GLOSSARY.md` line 16-18 as the chosen app accent
colour used to build the Material 3 colour scheme. In code it is:

- `AccentColor.seed: Color` — `app/src/main/java/com/kd/anddirstat/Theme.kt`, enum
  declared lines 51-70 (e.g. `GREEN` seed `Color(0xFF1DB954)`, line 60; `BLUE` seed
  `Color(0xFF2196F3)`, line 62).
- The seed is turned into a `SchemeVibrant` at lines 282-285
  (`val seed = accentColor.seed.toArgb()` line 282; `SchemeVibrant(hct, isDark, 0.0)`
  line 284) and fed into the `darkColorScheme`/`lightColorScheme` that
  `AndDirStatTheme` supplies to `MaterialTheme` (lines 288-356, 382-388).
- The currently selected accent is held as `accentColor` in `MainApp` — `MainActivity.kt`
  lines 240, 244, 246 — and passed into `AndDirStatTheme`.

So the Compose background should use `accentColor.seed` (the raw seed) rather than a
derived Material role such as `colorScheme.surface`, which is what `canvasBg` uses
today (`TreemapCanvas.kt` line 286-287). If the intent is the derived accent used for
UI, `MaterialTheme.colorScheme.primary` (also captured at `TreemapCanvas.kt` line
317, and computed from the same seed at `Theme.kt` lines 289 / 324) is the alternative —
but "seed colour" per the glossary is `AccentColor.seed`.

## Risks / caveats

- Transparency via `PixelFormat.TRANSPARENT` + surface behind the window can be a
  performance/compositing trade-off versus the current overlay; it requires a full
  alpha blend of the GL surface each frame. The project's AGENTS.md explicitly
  forbids changing the renderer or layout, so any such change must stay in the
  `AndroidView`/Compose configuration only.
- The status-bar and navigation-bar scrims (`TreemapCanvas.kt` lines 680-716) use
  `surfaceColor` with alpha; against a seed-colour background they will tint the top
  and bottom edges. Map #3 notes this is currently acceptable to keep for icon
  legibility.
- In pure-black mode `canvasBg` is `Color.Black` (line 287) and the theme forces
  `background`/`surface` to black (`Theme.kt` lines 360-365). Map #3 says the seed
  colour should be used in pure-black too, so this override would need to be bypassed
  for the treemap background.
- The app-resume blank-screen fix (renderer instance-buffer re-upload,
  `TreemapGLRenderer.kt` lines 135-139, 284-292) is unrelated and out of scope per
  map #3; none of the above touches it.
