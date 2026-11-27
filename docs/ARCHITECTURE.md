# Architecture and interfaces

## Design decision
Train on desktop; infer locally on all platforms. Keep a shared language-neutral profile format and reference implementation in Python. Do not assume Python can run the mobile engine efficiently. Use a native KataGo backend on Android (evaluate KataDroid integration) and a process-based JSON analysis interface on desktop. Evaluate KaTrain/Sabaki GTP and Kaya UI separately; Kaya is AGPL-3.0, not a permissive drop-in. Decide whether to fork after license and integration review. No upstream code is vendored yet.

```
SGF -> normalize/deduplicate -> position + target move -> engine features
    -> train/validate residual style -> portable profile
                                              |
GUI / touch / hardware adapter -> Game state -> local policy selector
                                              ^
                               KataGo + Human SL (local)
```

Game state owns rules, move history, turn, setup stones, ko/superko, passing, clocks, scoring and undo. Never trust a UI/hardware event as authoritative. Use an established rule implementation or KataGo, not a simplified board for production. Engine adapter returns legal moves, rank-conditioned probabilities and optional independent quality estimates. All engine calls have cancellation/deadlines and bounded buffers. Reference engine probe is synchronous and single query; production adapter must be persistent, asynchronous and multiplexed.

## Style model
For position s, legal move a, target rank r, master p and reference era e:

`logit(a) = log P_human(a|s,r) + lambda * clip(f_p(s,a), -c, c)`

`f_p` is a learned residual relative to a comparable professional/era baseline, not the probability of a perfect professional move. Train by conditional cross-entropy over legal candidates with baseline log-probability as an offset, L2 regularization, validation by full held-out games. Candidate set must contain the actual move. For production use full legal policy whenever possible; sampled candidates require consistent correction. Baselines: rank only, era-only, hand-crafted style bias, learned style. Try simple regularized linear residual before neural adapter/transfer learning. Avoid training a full network from hundreds of games.

Features: phase, distance to edge/corner, last move/locality, relative shape and liberties, capture potential, own/opponent groups, territory/influence estimates, alternative move score loss, uncertainty, opening patterns; encode eight dihedral symmetries consistently, handicap/rules/komi, era and opponent strength. Test features for confounding and ablation. Ownership and tactical estimates from strong KataGo are analytical features, not labels for style; using them in selection may increase strength and must be calibrated.

Strength r and style lambda interact. Fit temperature/strength adjustment on held-out games and controlled matches for each style × rank band × board size. Do not always select strongest move, force arbitrary blunders, or hard-filter all mistakes at low ranks. Use rank-specific safety constraints only if validated. Low lambda restores baseline; high lambda not guaranteed to preserve rank. Explicitly report this.

Profile schema: version, player_id, source hashes, model IDs, board size, era, feature version, residual coefficients, clipping, calibration table, metrics, licenses. No copyrighted SGF comments in exports. Example config is intentionally a placeholder; no trained weights.

## Device adapters
1. Touch screen: fullscreen UI; normal input and local storage; landscape, contrast, accessibility, large controls.
2. Physical electronic goban: transport adapter (vendor-supported BLE/USB/TCP), detect snapshot and stone events, show move via LED or screen. Adapter methods connect, snapshot, highlight, clear, disconnect. State machine: await human placement -> validate -> wait for captures removed -> compute AI move -> display -> wait for confirmation -> reconcile. Deduplicate events, reject wrong color/occupied cells, handle rotation/mirroring, reconnect from full snapshot. Use mock/simulator before hardware; do not send undocumented commands until hardware and vendor terms verified. Izis SDK is unofficial and may not be safe for arbitrary hardware.
3. Camera: optional board recognition; confirm uncertain positions, handle lighting and occlusion. No cloud required.

## Offline packaging
Windows bundle includes runtime, selected engine binaries, config, weights, profiles and licenses; Android includes ARM64 runtime and validated compact model or imports a signed/checksummed local model pack. Android Human SL parity and latency are open risks; a server-only mode is not a substitute. Test cold start after installation with networking blocked. Do not require online auth. Training remains desktop-first; copy small validated profiles to mobile by local file/USB. Optional LAN board bridge uses no Internet. Keep SQLite game library and JSON profile manifests with migrations and backup/export. Model updates opt-in; verify hashes, no automatic remote code execution.
