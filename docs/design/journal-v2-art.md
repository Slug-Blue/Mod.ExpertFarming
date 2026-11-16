# Journal v2 — GUI art (WR-181h)

Art for the v2 field journal (spec `journal-v2.md` §3.0, §3.13, §4.5, §5.5). This note fixes the UV layout the spec leaves open; code reads the sprites by these coordinates.

## Files

All four atlases are in `assets/wildroots/textures/gui/journal/`. The spec puts them directly in `textures/gui/`. The task kept the art in its own folder, so the resource locations are `wildroots:textures/gui/journal/<file>`.

| File | Size | Contents |
|---|---|---|
| `journal.png` | 512 × 512 | book spread, nav strip, tabs, ribbons, buttons, search field, scene controls, step slider, scene frame, 2D-fallback arrows |
| `journal_icons.png` | 64 × 64 | category icons 16 × 16, 4 × 3 grid |
| `journal_scene.png` | 64 × 32 | scene billboards 16 × 16, label callout 9-slice |
| `trait_bar.png` | 64 × 32 | trait bar segments 6 × 5 and flag icons 9 × 9 (shared with the lens panel; whoever moves `TraitBar` to sprites may move the file to `textures/gui/`) |

All four files are 8-bit RGBA PNGs with no metadata chunks, as `AssetIntegrityTest` requires. Together they take 23.5 KiB (the budget is 80 KiB). That test also requires every texture to be 16 px wide, a rule written for block and item tiles. GUI atlases cannot meet it, so the test needs an exemption for `textures/gui/`. Unused atlas space is transparent. Use `blit(..., 512, 512)` for `journal.png`, and pass the matching size for each of the others.

## Book geometry (`spread`, 288 × 180)

- Cover border: leather, with brass corners.
- Paper: left page x 6–142, right page x 146–282, y 5–173. The spine gutter is x 142–146 and the stacked page edges are y 173–175.
- Text areas, 116 × 148 each: left at (16, 20), right at (156, 20). The header row is y 7–17, with a faint rule at y 18.
- Paper `#F4EEDE` is lighter than vanilla and low in contrast. Ink `0x3A2A1A`, link `0x2B5C8A` and new `0x2E7D32` all read on it.
- Tabs (wider after owner playtest 2, 2026-11-15): draw each tab at `book.x - 22`. The tab is 34 wide; its right 12 px go under the cover, so draw tabs before the spread. `tab_selected` is paper-coloured and full width, so it joins the page. Place the 16 × 16 icon at tab +3, +2, wholly outside the book (`book.x - 19` … `book.x - 3`). The book with its tabs is 310 × 200 and still fits 320 × 240. Draw `new_dot` at the tab's top-left corner. Bookmark ribbons go under the tabs, at `book.x - 9`.
- Nav strip: `nav_bar` at (0, 182) relative to the book. The nav buttons are 16 × 16 on top of it.
- Scene page (§3.13; window 102 high since owner playtest 3, the caption needs three lines in Russian): window 264 × 102 at book (12, 22). Draw `scene_frame` 9-slice (border 4) on the window grown by 4, so the frame covers y 18–128 and also the spine. The caption takes y 130–157 (three lines, the rest cut with an ellipsis and shown whole as a tooltip) and the controls row y 159–171.

## UV table

Multi-state sprites are listed by their first cell. The other states are the cells directly below it, in the order given in the Note column.

| Atlas | Sprite | u | v | w | h | Note |
|---|---|---|---|---|---|---|
| journal | `spread` | 0 | 0 | 288 | 180 | cover, two pages, spine, page stack (geometry above) |
| journal | `nav_bar` | 0 | 184 | 288 | 16 | strip under the book; leather at x 4–284 |
| journal | `tab_normal` | 0 | 212 | 34 | 20 | category tab; icon at +3,+2; right 12 px under the cover |
| journal | `tab_hover` | 34 | 212 | 34 | 20 | same |
| journal | `tab_selected` | 68 | 212 | 34 | 20 | same, paper-coloured |
| journal | (old tabs) | 292 | 0 | 24 | 20 ×3 | the first, narrower tabs; unused since playtest 2, kept as the source of the wide ones (made by stretching column 11 of each by 10 px) |
| journal | `ribbon_normal` | 364 | 0 | 12 | 16 | user bookmark ribbon, notch on the left |
| journal | `ribbon_hover` | 376 | 0 | 12 | 16 | same |
| journal | `new_dot` | 388 | 0 | 7 | 7 | green "new" dot with outline |
| journal | `btn_back` (◄◄) | 292 | 24 | 16 | 16 | rows: normal v24, hover v40, disabled v56 |
| journal | `btn_prev` (◄) | 308 | 24 | 16 | 16 | same rows |
| journal | `btn_home` (⌂) | 324 | 24 | 16 | 16 | same rows |
| journal | `btn_next` (►) | 340 | 24 | 16 | 16 | same rows |
| journal | `btn_forward` (►►) | 356 | 24 | 16 | 16 | same rows |
| journal | `btn_search` (magnifier) | 372 | 24 | 16 | 16 | same rows |
| journal | `parent` (header ◄) | 292 | 72 | 12 | 12 | no plate; rows normal v72, hover v84, disabled v96 |
| journal | `star_empty` | 304 | 72 | 12 | 12 | same rows |
| journal | `star_full` | 316 | 72 | 12 | 12 | same rows |
| journal | `search_field_normal` | 328 | 72 | 24 | 14 | 9-slice, border 3; text at +4,+3 |
| journal | `search_field_focused` | 352 | 72 | 24 | 14 | same |
| journal | `scene_step_back` (⏮) | 292 | 112 | 12 | 12 | rows: normal v112, hover v124, disabled v136, active/on v148 |
| journal | `scene_play` (▶) | 304 | 112 | 12 | 12 | same rows |
| journal | `scene_pause` (⏸) | 316 | 112 | 12 | 12 | same rows |
| journal | `scene_step_fwd` (⏭) | 328 | 112 | 12 | 12 | same rows |
| journal | `scene_restart` (↻) | 340 | 112 | 12 | 12 | same rows; the label "again" is text drawn after it |
| journal | `scene_reset_view` (⟲) | 352 | 112 | 12 | 12 | same rows |
| journal | `scene_view_2d` (2D) | 364 | 112 | 12 | 12 | same rows; `active` = toggle on |
| journal | `slider_track_empty` | 292 | 164 | 8 | 3 | tile horizontally |
| journal | `slider_track_filled` | 300 | 164 | 8 | 3 | tile horizontally (the part already passed) |
| journal | `slider_dot_past` | 310 | 162 | 7 | 7 | centre at +3,+3 |
| journal | `slider_dot_current` | 318 | 162 | 7 | 7 | same |
| journal | `slider_dot_future` | 326 | 162 | 7 | 7 | same |
| journal | `slider_dot_hover` | 334 | 162 | 7 | 7 | same |
| journal | `slider_handle_normal` | 344 | 161 | 5 | 9 | centre x at +2 |
| journal | `slider_handle_hover` | 350 | 161 | 5 | 9 | same |
| journal | `slider_handle_disabled` | 356 | 161 | 5 | 9 | same |
| journal | `scene_frame` | 292 | 176 | 16 | 16 | 9-slice, border 4, centre transparent; drawn on the window grown by 4 |
| journal | `scene_backdrop` | 312 | 176 | 16 | 16 | optional sky behind the scene; stretch it |
| journal | `arrow_right` | 332 | 176 | 8 | 8 | 2D-fallback arrow, ink, no plate |
| journal | `arrow_down` | 340 | 176 | 8 | 8 | same |
| journal | `arrow_left` | 348 | 176 | 8 | 8 | same |
| journal | `arrow_up` | 356 | 176 | 8 | 8 | same |
| journal | `arrow_loop` | 364 | 176 | 8 | 8 | same, for ring layouts |
| journal | `frame_highlight` | 372 | 176 | 8 | 8 | 9-slice, border 2: current step in the 2D fallback frame |
| journal | `focus_ring` | 382 | 176 | 6 | 6 | 9-slice, border 2: keyboard focus or hover on any widget |
| journal_icons | `first_steps` | 0 | 0 | 16 | 16 | sprout in soil |
| journal_icons | `plant` | 16 | 0 | 16 | 16 | plant and harvest |
| journal_icons | `traits` | 32 | 0 | 16 | 16 | lens and traits (bars) |
| journal_icons | `pollination` | 48 | 0 | 16 | 16 | selection and pollination |
| journal_icons | `cultivars` | 0 | 16 | 16 | 16 | cultivar tag |
| journal_icons | `species` | 16 | 16 | 16 | 16 | leaf |
| journal_icons | `lines` | 32 | 16 | 16 | 16 | pedigree |
| journal_icons | `notes` | 48 | 16 | 16 | 16 | pinned note |
| journal_icons | `contents` | 0 | 32 | 16 | 16 | open book |
| journal_icons | `bookmarks` | 16 | 32 | 16 | 16 | ribbon with star |
| journal_icons | `search` | 32 | 32 | 16 | 16 | magnifier (search results) |
| journal_icons | `unknown` | 48 | 32 | 16 | 16 | not yet found |
| journal_scene | `hand` | 0 | 0 | 16 | 16 | anchor bottom-centre (+8, +15) |
| journal_scene | `hand_lot` | 16 | 0 | 16 | 16 | same anchor |
| journal_scene | `hand_brush` | 32 | 0 | 16 | 16 | same anchor |
| journal_scene | `clock` | 48 | 0 | 16 | 16 | "waits for harvest"; anchor centre |
| journal_scene | `arrow_loop` | 0 | 16 | 16 | 16 | anchor centre |
| journal_scene | `callout` | 16 | 16 | 12 | 12 | 9-slice, border 3; text at +3,+2, bubble 13 tall for one line |
| journal_scene | `callout_tail` | 30 | 16 | 7 | 4 | under the bubble, centred, overlapping its bottom outline by 1 px |
| trait_bar | `segment_filled` | 0 | 0 | 6 | 5 | the 1 px frame stays drawn by code |
| trait_bar | `segment_empty` | 8 | 0 | 6 | 5 | |
| trait_bar | `flag_bitter` | 0 | 8 | 9 | 9 | |
| trait_bar | `flag_pollination_B` | 9 | 8 | 9 | 9 | |
| trait_bar | `flag_pollination_H` | 18 | 8 | 9 | 9 | |
| trait_bar | `flag_pollination_N` | 27 | 8 | 9 | 9 | |
| trait_bar | `flag_pollination_S` | 36 | 8 | 9 | 9 | |
| trait_bar | `flag_ailing` | 45 | 8 | 9 | 9 | |
| trait_bar | `flag_like_cultivar` | 54 | 8 | 9 | 9 | "looks like a cultivar" |

## Notes for code

- Buttons have their own hover row with a green outline. For any other widget, use `focus_ring` or a 1 px `0x2E7D32` frame.
- Play and pause are two sprites; swap between them. The `2D` toggle uses the `active` row when it is on.
- Slider: tile the track along the dot row (filled up to the current step, empty after it), then draw the dots, then the handle.
- Billboards have a dark outline and a soft cream halo, so they read on grass, farmland and dark soil. Draw them at 16 GUI px, never smaller than 8 (§3.13).
- The images were made by a generator script that is not in the repo. The PNGs here are the source of truth.

## Check by hand in runClient

- Text readability on the paper at GUI scale 1–4.
- The tab and ribbon overlap at the cover edge.
- The scene frame drawn over the spine.
- Billboards drawn over the real grass and farmland textures.
