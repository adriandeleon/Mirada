# Mirada

**A fast, folder-oriented image viewer.** Double-click a photo, look, arrow through the folder,
close. Java 25 + JavaFX 27.

**Status:** early. JPEG, PNG, GIF and BMP; photos turned upright from their EXIF orientation.

## Keys

| Key | Does |
|---|---|
| Right, Page Down, Space | Next image |
| Left, Page Up, Backspace, Shift+Space | Previous image |
| Home / End | First / last image |
| `+` / `-` | Zoom in / out (the wheel zooms about the pointer) |
| `0` / `1` | Fit to window / actual size (double-click toggles them) |
| `R` / Shift+`R` | Rotate right / left |
| `F`, F11 | Full screen |
| Escape | Leave full screen, or close |
| Ctrl+O | Open |
| Ctrl+W, Ctrl+Q | Close |

Drag to pan a zoomed image. Drop a file or folder on the window to open it.

## Building

```
./mvnw javafx:run -Djavafx.args=/path/to/photo.jpg
./mvnw verify
```

JDK 25 is required.

## Licence

MIT. See [LICENSE](LICENSE).
