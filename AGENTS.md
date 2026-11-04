# Agent Instructions

## Agent skills

### Issue tracker

Issues and specs live in GitHub Issues using the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical triage labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context repository layout (`GLOSSARY.md` + `docs/adr/`). See `docs/agents/domain.md`.

## Treemap GPU Rendering Architecture (CRITICAL - DO NOT BREAK)

- **Pure Squarified Layout**: `TreemapLayout.kt` computes the exact, unmodified Bruls et al. squarified layout for all files. Never fold directory nodes or alter squarified aspect ratio computation.
- **Hardware OpenGL ES 3.0 Instanced Renderer**: `TreemapGLRenderer.kt` renders all tiles in **a single instanced draw call** (`glDrawArraysInstanced`) using a dedicated `GLSurfaceView` thread.
  - Per-tile data (rect and 3-step cushion gradient colors) is packed into an instance VBO.
  - Zoom and pan are uniform vectors (`uScale`, `uOffset`, `uResolution`) passed to the vertex shader. CPU work during pinch/pan/zoom is `0.001ms`.
  - True van Wijk & van de Wetering Cushion Treemap (CTM) lighting is calculated in the fragment shader in parallel across GPU cores.
- **Canvas Bottleneck Avoidance**: Never revert to looping `Canvas.drawRect` or software bitmap generation for treemap tiles. 50,000 Compose/Skia JNI calls per frame choke the main UI thread (~100ms/frame). OpenGL ES 3.0 instancing executes in **~0.3ms** on the GPU, guaranteeing rock-solid 60–120 FPS.
- **Compose Overlay Layer**: Gestures, selection spring physics, and popups live in a lightweight Compose overlay directly on top of the `GLSurfaceView`.

