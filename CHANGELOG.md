# Changelog

All notable changes to **AndDirStat** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.4] - 2026-09-23

### Added
- **Landscape Nameless Navigation Rail**: Docked navigation rail with branded logo header, nameless icon buttons, and animated active indicator pills in landscape orientation.
- **Spring-Animated Tile Preview Popup**: Full-cover preview popup with pure spring physics for entry, tile-to-tile glides, and exit animations upon dismissal.
- **Pass-through Tap Tile Triggering**: Dismissal by tapping any canvas tile immediately selects and shifts the popup to that tile.
- **Shape-Based Scanning Animation & Scanned Files Stream**: Real-time streaming log display during filesystem scans.

### Changed
- **Cleaner List Grouping**: Grouped list items with dynamic corner rounding in duplicates, empty folders, and recycle bin cleaner views.
- **Swipe-to-Action Gestures**: Restored left/right swipe gestures on screenshot cleaner items.
- **Storage Trend Graph Performance**: Optimized rendering and fluid entrance transitions on storage trend screens.

---

## [1.0.3] - 2026-08-31

### Added
- **2D Cushion Treemap Aspect-Ratio Shading**: Cushion lighting dynamically stretches and squeezes along tile dimensions according to van Wijk & van de Wetering CTM specs.
- **Hardware-Aware Memory Tiering**:
  - `< 4GB RAM`: Solid fill tiles with minimal memory footprint.
  - `4GB - 6GB RAM`: Directional linear gradients.
  - `8GB+ RAM`: Full 2D quadratic parabolic cushion shaders.
- **Treemap Hold-and-Glide Follow**: Info preview card dynamically glides in real time with the active touch pointer and hovered tiles.
- **System-Scale Motion Physics**: All navigation and UI animations transition to Material Design 3 physics springs (`Spring.StiffnessMedium`) with zero hardcoded durations, automatically adapting to system animator scales and accessibility preferences.
- **Instant Zero-Latency Screen Swapping**: Route transitions mount destinations synchronously on the tap frame while the bottom navigation capsule animates concurrently.

### Changed
- **Predictive Back Navigation**: System back transitions updated to pure physics-based horizontal slides with zero fade.
- **Popup Action Layout**: Swapped positions in detail popup — `Select`/`Deselect` on the left, `Total: <size>` / `Show in Folder` on the right.
- **Apps Detail Action**: Replaced "Show in Folder" with "Total: <size>" for installed applications.
- **Clean App Labels**: System apps display clean package labels without `(System)` suffixes, tracking system status internally via `AppPackageRegistry`.
- **Cleaner UI Streamlining**: Standardized selection badges across cleaner activities, streamlined action button labels (removed `(x)` count indicators), and added dynamic selection counts to app headers.
- **Discover Screen Refinements**: Changed Discover back button to an inline search trigger and streamlined slide-to-action to dedicated uninstall.

---

## [1.0.2] - 2026-08-27

### Added
- **FileMaintenanceEngine**: Centralized batch file deletion, starred protection checks, and media store updates.
- **Settings Transition Direction**: Proper directional slide matching navigation hierarchy.

### Changed
- **Architecture Simplification**: Collapsed separate cleaner activity wrappers into unified Compose navigation destinations.

---

## [1.0.1] - 2026-08-23

### Added
- **Multi-Volume Storage Selector**: Support for SD cards, USB OTG drives, and internal storage.
- **Predictive Back Gestures**: Android 14+ predictive back support.
- **Dynamic Color & AMOLED Themes**: Material You dynamic color generation and pure AMOLED pitch black mode.
- **Cleaners Suite**: Dedicated cleaners for Duplicate Files, Empty Folders, Screenshots, and Recycle Bin.
- **Storage Trend Insights**: Daily storage delta tracker and consumption forecasting.

---

## [1.0.0] - 2026-08-22

### Added
- Initial release of AndDirStat — fast, modern Material 3 disk usage analyzer for Android.
- Interactive squarified treemap canvas with multi-touch zoom and pan.
- Hierarchical Explorer view with inline file actions.
- File Types breakdown and storage categorization.
- Discover dashboard with large file discovery and app storage analysis.
