# Repository Guidelines

## Project Structure & Module Organization
`src/web/` contains the deliverables for UI prototypes. Each version lives in its own folder (`v0/` through `v6/`) and keeps related HTML, images, and topology notes together. Use `docs/` for design or architecture notes, `config/` for environment-specific settings, `data/` for sample inputs, `scripts/` for local utilities, and `tests/` for future automated coverage.

Examples:
- `src/web/v0/data_flow.md`: source topology definition
- `src/web/v5/index-latency-monitor-v5.html`: versioned preview page
- `docs/ARCHITECTURE.md`: high-level system notes

## Build, Test, and Development Commands
This repository currently ships static HTML prototypes rather than an app build pipeline.

- `python3 -m http.server 8000 -d src/web`: serve local previews
- `open src/web/v6/index-latency-monitor-v6.html`: open a page directly on macOS
- `git status`: review local changes before committing
- `rg "pattern" src/web`: search quickly across versions

If you add scripts, document them in `scripts/README.md` and keep them non-destructive.

## Coding Style & Naming Conventions
Use 2-space indentation in HTML, CSS, Markdown, and inline JavaScript. Prefer semantic sectioning and keep CSS variables in `:root` for theme tokens. Use lowercase, hyphenated file names for web assets, and versioned page names such as `index-latency-monitor-v6.html`. Keep labels consistent with domain terminology: `TKO1`, `TKO2`, `SKM`, `L1`, `L2`, `L3`, `BG`, `HCS`.

## Testing Guidelines
There is no automated test suite yet. For UI changes, validate by opening the affected HTML file in a browser and checking desktop responsiveness at common widths. When topology or latency data changes, verify that labels, route directions, and center ordering match `src/web/v0/data_flow.md`. Add future tests under `tests/` using names like `test_<feature>.py` or `<feature>.spec.js`.

## Commit & Pull Request Guidelines
Follow the existing commit style: short imperative messages, often with prefixes such as `feat(frontend):`, `chore(web):`, or `chore:`. Keep commits scoped to one change set. Pull requests should include:
- a short summary of the UI or topology change
- linked issue or task reference when available
- screenshots or screen recordings for visual changes
- notes on any assumptions made about data flow or latency rules

## Contributor Notes
Do not overwrite prior prototype versions. Create a new versioned file or folder for substantial redesigns, and preserve earlier mockups for comparison.
