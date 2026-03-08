# rt-poc

Latency monitor POC rebuilt as a Spring Boot application. The backend simulates topology metrics and pushes full snapshots to the v8 page over SSE.

## Run Locally

- `mvn spring-boot:run`
- Open `http://localhost:8080/`
- Direct page URL: `http://localhost:8080/web/v8/index-latency-monitor-v8.html`
- Snapshot endpoint: `http://localhost:8080/api/latency/snapshot`
- SSE endpoint: `http://localhost:8080/api/latency/stream`

## Package

- `mvn -DskipTests package`
- Output jar: `target/rt-poc-0.0.1-SNAPSHOT.jar`

## Docker

- `docker build -t rt-poc .`
- `docker compose up --build`

The application is configured to listen on port `8080`. Static assets under `src/web/` are copied into the Spring Boot jar and served from `/web/**`.

## v8 Link Visual Classification

The following table summarizes how links in [src/web/v8/index-latency-monitor-v8.html](/opt/repo/rt-poc/src/web/v8/index-latency-monitor-v8.html) are visually classified by line weight and dash style. This is based on the current `.route-flow*`, `.route-base*`, and `routeVariantClass()` rules.

| Category | Flow style | Base style | link.id |
| --- | --- | --- | --- |
| Thick / standard intra | `4px`, `dash 14 10` | `2px`, solid | `TKO1-L1__TKO1-L2`, `TKO2-RT__TKO2-L1`, `TKO2-BB__TKO2-L1`, `TKO2-OMDC__TKO2-L1`, `TKO2-CIIC__TKO2-L1`, `TKO2-BJ__TKO2-L1`, `TKO2-L1__TKO2-L2`, `SKM-L1__SKM-L2`, `TKO2-L2__TKO2-L3`, `TKO2-L3__TKO2-BG`, `TKO2-BG__TKO2-HCS` |
| Thick / standard cross | `4px`, `dash 16 11` | `2px`, solid | `TKO2-L3__TKO1-BG`, `TKO2-L3__SKM-BG` |
| Medium / feedback-like | `3px`, `dash 4 16` | `2px`, `dash 4 10`, `opacity 0.8` | `TKO1-L2__TKO1-L3`, `SKM-L2__SKM-L3`, `TKO1-L2__TKO2-L3`, `SKM-L2__TKO2-L3`, `TKO2-L2__TKO1-L3`, `TKO2-L2__SKM-L3` |
| Medium / standard feedback | `3px`, `dash 4 16` | `2px`, `dash 4 10` | `TKO1-BG__TKO1-L3`, `TKO1-BG__TKO2-L3`, `SKM-BG__SKM-L3`, `SKM-BG__TKO2-L3`, `TKO2-BG__TKO1-L3`, `TKO2-BG__SKM-L3`, `TKO1-L3__TKO1-L1`, `TKO1-L3__TKO2-L1`, `SKM-L3__SKM-L1`, `SKM-L3__TKO2-L1`, `TKO2-L3__TKO1-L1`, `TKO2-L3__SKM-L1` |
| Medium / feedback forward-dashed | `3px`, `dash 16 11` | `2px`, `dash 4 10` | `TKO2-BG__TKO2-L3`, `TKO2-L3__TKO2-L1` |
| Thin / soft-dashed | `2.6px`, `dash 3 18`, `opacity 0.92` | `2px`, `dash 3 11`, `opacity 0.72` | `TKO1-RT__TKO1-L1`, `TKO1-BB__TKO1-L1`, `TKO1-OMDC__TKO1-L1`, `TKO1-CIIC__TKO1-L1`, `TKO1-BJ__TKO1-L1`, `SKM-RT__SKM-L1`, `SKM-BB__SKM-L1`, `SKM-OMDC__SKM-L1`, `SKM-CIIC__SKM-L1`, `SKM-BJ__SKM-L1`, `TKO1-BG__TKO1-HCS`, `TKO1-BG__TKO2-HCS`, `SKM-BG__SKM-HCS`, `SKM-BG__TKO2-HCS`, `TKO2-BG__TKO1-HCS`, `TKO2-BG__SKM-HCS` |

Notes:

- `warn` and `critical` only change line color, not line width or dash pattern.
- Every rendered link also has a `route-base` path under the animated `route-flow` path.
- If a new `link.id` is added in `v8`, the final visual category depends on both its `kind` and whether `routeVariantClass()` returns a variant such as `soft-dashed`, `feedback-like`, or `forward-dashed`.
