# Contributing

Thanks for looking at this. Issues and PRs are genuinely welcome — especially
from anyone who spots something I got wrong.

## Ground rules (please keep these)

These three are not style preferences; they are load-bearing.

1. **`solver/` and `vision/BoardReader.kt` must stay free of Android imports.**
   That is what lets the whole solver and the whole vision pipeline run in
   plain JVM unit tests against real screenshots. Adding one `import
   android.*` to either package breaks the testing story.

2. **No input injection.** No `AccessibilityService`, no touch synthesis, no
   "just this once" automation. The app advises; the human plays. See
   [Why suggest-only](../README.md#why-suggest-only-).

3. **No guessed constants.** If you need a colour or a coordinate, measure it
   and write down how. `tools/detect_final.py` is the tool for this:

   ```bash
   python3 tools/detect_final.py samples/shot1.jpg samples/shot2.jpg samples/shot3.jpg
   ```

## Workflow

```bash
git checkout -b my-change
# ... edit ...
./gradlew test        # CI runs exactly this; keep it green
git push origin my-change
```

## If you touch the vision code

Add or update a test in `SampleScreenshotTest` using a screenshot in
`samples/`. If you change a threshold, include the measurement that justifies
it in a comment — a number with no provenance is a liability.

## If you touch the solver

Add a test in `BlockBlastSolverTest` that would fail without your change.
The solver's correctness claim is "optimal under the heuristic, within the
time budget" — if you change the search, that claim has to stay true.

## Reporting a bug

Include your phone model, Android version, screen resolution, and a
screenshot. For vision bugs, the *Calibrate* tab shows what the app actually
sees — a screenshot of that is worth ten descriptions.