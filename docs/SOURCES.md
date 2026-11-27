# Source register (2026-11-27)

Sources are references, not bundled files. Web pages were checked where marked. The preparation runtime could not resolve external download hosts; scripts retrieve materials locally once network access is available. Public accessibility does not necessarily imply permission to redistribute.

| ID | Source | Use / status |
|---|---|---|
| katago | https://github.com/lightvector/KataGo | Core engine; verify LICENSE and bundled dependencies at chosen commit |
| analysis docs | https://github.com/lightvector/KataGo/blob/master/docs/Analysis_Engine.md | JSON protocol and Human SL guide; verified |
| human-sl | https://katagotraining.org/extra_networks/ | Human SL model; check weight terms; official b18c384nbt-humanv0.bin.gz also at https://github.com/lightvector/KataGo/releases/tag/v1.15.0 |
| main-weights | https://katagotraining.org/networks/ | Choose compatible normal model after hardware benchmark; record model ID/hash/license |
| engine releases | https://github.com/lightvector/KataGo/releases | Platform binaries and matching configs |
| katrain | https://github.com/sanderland/katrain | Desktop coaching and rank calibration; check version license and dependencies |
| sabaki | https://github.com/SabakiHQ/Sabaki | SGF editor and GTP UI; review license |
| katadroid | https://github.com/zhzy0077/KataDroid | MIT Android code, native KataGo/LiteRT; Human SL support not established; third-party terms separate |
| badukai | https://aki65.github.io/ | Android reference application; no assumption of source redistribution rights |
| kaya | https://github.com/kaya-go/kaya | AGPL-3.0 UI; desktop/web/PWA, mobile touch and ONNX; test offline engine, not just UI |
| cwi-sgf | https://homepages.cwi.nl/~aeb/go/games/ | >90k professional SGF; publisher states public domain; archive 46 MB; review annotations/third-party rights |
| cwi archive | https://homepages.cwi.nl/~aeb/go/games/games.tgz | Direct download checked via link; not downloaded here |
| PAGE | https://github.com/yifangao112/PAGE | 98,525 annotated games, ~59.6 GB; CC BY-NC-SA 4.0 for dataset, MIT repo code; OneDrive/Baidu links/password on README; non-commercial only; start with metadata, not entire dataset |
| jgdb | https://pjreddie.com/projects/jgdb/ | >500k mixed professional/top amateur games, limited metadata; verify terms, not priority for named players |
| professional candidate | https://github.com/Gifanan/Professional-Go-Dataset | Earlier lead; URL not verified this turn; no automatic download |
| GoGoD | https://gogodonline.co.uk/ | Paid archive; optional only, no copying |
| OGS | https://docs.online-go.com/ | API documentation; check terms and rate limits before collecting public games |
| Drunken Master | https://online-go.com/player/1766503/ | Prior conversation lead; verify account identity before acquisition; no code confirmed |
| izis | https://github.com/wjchen-vlsi/izis_go_board_reng | Unofficial protocol/simulator; check license and hardware risks before using |
| igoki | https://github.com/cmdrdats/igoki | Physical camera/projector integration example |
| historical masters paper | https://www.researchgate.net/publication/390736598_Player_Models_of_Go_Historical_Master_Figures | Transfer learning for named Go masters; obtain lawful full text and verify authors/DOI before formal citation |
| GoStyle | https://gostyle.j2m.cz/ | Player attribute/style estimation literature lead |
| SGF spec | https://www.red-bean.com/sgf/ | SGF FF[4] reference; implementation must account for encodings, variations, setup |
| GTP | https://www.lysator.liu.se/~gunnar/gtp/ | Protocol for engine integration |

## Acquisition policy
Use the provided script for CWI archive and upstream code checkouts. Clone, don't paste entire external repos into main Git history. Record commits and preserve notices. Data and model weights stay local or in separately licensed release assets. PAGE is optional and its share-alike/non-commercial conditions need specific review before publishing derived model packs. Do not blindly extract remote archives: verify archive entries and checksums, reject traversal and symlinks. Downloading all datasets is unnecessary and wasteful; CWI first, targeted professional subsets second. The starter corpus is synthetic for tests only.
