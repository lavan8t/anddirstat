# AndDirStat

A storage analyser for Android. It shows disk use as a treemap and helps the user find and remove files.

## Language

**Treemap**:
A view that shows files as rectangles. Each rectangle is a file or folder, and its area shows the file size.

**Tile**:
One rectangle in the treemap. It shows one file or folder.

**Treemap background**:
The area behind the tiles and around the treemap. It shows when the treemap does not fill the screen, for example at high zoom.

**Theme seed colour**:
The colour chosen for the app accent. It is used to build the Material 3 colour scheme.
_Avoid_: accent seed, seed

**Ambient blob**:
A blurred circle drawn behind the treemap to colour the margin. This effect is being removed.
_Avoid_: halo, ambient glow
