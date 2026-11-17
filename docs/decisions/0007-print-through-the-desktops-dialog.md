# 0007. Print through the desktop's own dialog: GTK on Linux, AWT headful on Windows

Accepted, 2026-11-16.

## Context

File → Print should hand the image to the operating system's own print dialog, the one every other
app on that desktop shows, with its printers, paper, copies and Print to File. JavaFX prints through
`javafx.print`, which drives AWT's printer job: on the Mac and Windows that job shows the system's
dialog (NSPrintPanel, the Win32 print dialog), on Linux a Swing dialog of AWT's own. Under a
headless AWT, `PrinterJob.showPrintDialog` shows nothing and answers yes, so the page goes straight
to the default printer.

Mirada ran AWT headless everywhere but the Mac, as CLI-09 asks (Pauta 6.1.0): only the tray, the
dock badge, the Mac's open-file handlers and Move to Trash may make it headful.

## Decision

- **Linux**: GTK's print dialog through FFM (`com.mirada.print.GtkPrint`): a one-page
  `GtkPrintOperation` run with its dialog, whose `draw-page` comes back to Java and paints the image
  with cairo. JavaFX's glass already runs GTK 3 on the FX thread, so nothing new loads, and AWT stays
  headless.
- **macOS**: `javafx.print` (`FxPrint`). AWT was headful there already, for Move to Trash.
- **Windows**: `javafx.print` too, which needs AWT headful there: a deviation from CLI-09 (a
  SHOULD), recorded in `pauta.json`. The rule's worry is the Mac, where AWT and JavaFX share one
  AppKit run loop; on Windows each has its own message loop, and AWT's toolkit loads only when the
  first print starts.

## Consequences

- Each desktop shows its own dialog; nothing of the dialog is Mirada's to translate or keep up.
- Two code paths: GTK's is tested here (the drawing, into a cairo PDF); the Mac's and Windows' only
  by hand, as no test may reach a printer (`ViewerWindow.printWith` replaces the printer in tests).
- The native image needs the GTK and cairo downcalls and the `draw-page` upcall in its metadata, by
  hand; on the Mac and Windows, AWT's printing classes must be recorded by the tracing agent there
  with a print made, or printing fails in the native image only.
- Windows' Win32 print dialog could later replace AWT through FFM (`PrintDlgExW` and GDI), ending
  the deviation.
