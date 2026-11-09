# Start-up of the jlink image

History, kept for comparison: what the jlink image measured before the native image and
libjpeg-turbo. Today's numbers, the ones to beat, are in
[building-and-packaging.md](../building-and-packaging.md#start-up-measured).

Launch to the first image on screen, Linux x64 (this machine), a small JPEG, median of 6 warm runs.

| Build | ms |
|---|---|
| `scripts/dev-run.sh` (system JDK, jars on the module path) | 455 |
| App image, jpackage's launcher, no AOT cache | 812 |
| App image, jpackage's launcher, AOT cache | 577 |
| App image, script launcher, no AOT cache | 476 |
| App image, script launcher, AOT cache, before the header-bar UI | 246 |
| **The same, with the header bar, controls, sidebar and palette styles (what `-Pdist` builds now)** | **285–291** |

With everything on, `main` is reached at about 160 ms, the window is up 60 ms later, and the image is
decoded and painted in the next 30. A HEIC as the first image costs about 60 ms more. Stepping onto
a prefetched neighbour is 2–8 ms for every format.
