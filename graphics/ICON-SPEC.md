# App icon spec — "IN Messages"

Status: proposed. The launcher icon is the upstream Fossify artwork with a small
**IN** added, so that the app reads as its own product while keeping the mark people
already recognise. This file is the handoff for producing that artwork.

## What Android asks for

An adaptive icon has two independent layers:

1. **Background** — one flat colour, supplied by the app as a resource, one colour per
   launcher-icon variant. Not part of the artwork.
2. **Foreground** — the mark on a fully transparent background. This is the only
   artwork file, and **one asset serves every variant**.

Consequence: the foreground cannot know which background it is drawn on. Legibility
therefore has to come from the ink colour, which is why this spec asks for **two**
foreground assets instead of one (see *Contrast*).

The launcher icon is a static resource: it cannot follow the app's *Customize colours*
screen, and launchers cache it besides. "Compatible with the app's theming" here means
the ink flips the same way the app flips its own text and background — dark ink on a
light icon colour, light ink on a dark one.

## Canvas and safe zone (numbers are at 512 × 512)

- Canvas: **512 × 512**, matching the existing vector's viewport.
- Launchers mask the canvas as a circle, squircle, rounded square or teardrop.
- Safe circle: **radius 156 px** around the centre (256, 256) — the 66 dp safe zone.
  Anything outside it can be cropped by the tighter masks.
- Keep the mark's visual mass inside **radius 140 px**; leave ≥ 24 px of clear space
  between the mark and the safe circle.

A badge in the bottom-right corner therefore has to satisfy
`distance_from_centre + badge_radius ≤ 156`. Two placements work:

| Placement | Badge diameter | Centre | Reaches | Verdict |
|---|---|---|---|---|
| Corner stamp | ≤ 80 px | (338, 338) | 156 px | fits, but ~7 px at 48 dp — unreadable |
| Corner badge | 150–170 px | (300, 300) | ~147 px | fits, ~15 dp at 48 dp — readable |

The small corner version is not worth shipping: at the 48 dp size launchers use, the
letters collapse into a smudge. Either use a **150–170 px badge** or put the letters
**inside** the bubble.

## Adding the IN

**Option A — badge (preferred if the bubble stays as it is)**

- Circular badge, diameter **150–170 px**, centre at **(300, 300)**.
- Bubble scaled to **≤ 82 %** of its current size and shifted up-left so the badge
  overlaps its lower-right corner rather than colliding with the text lines.
- Where the badge overlaps the bubble, leave a **16–20 px** gap in the *background*
  colour (a ring punched out of the bubble), so the shape reads as separate.
- IN cap height **96–110 px**, centred in the badge.

**Option B — inline (most legible, most confident)**

- Replace the bubble's bottom line with **IN**, sitting where that line is: left
  aligned with the other two lines, cap height **70–80 px**, total width **90–110 px**.
- The bubble keeps its current size; nothing moves.
- The two remaining lines stay as they are.

**Option C — corner stamp** — 80 px badge at (338, 338). Only legible at 144 dp and
above. Documented for completeness; not recommended.

In every option: **IN is drawn as paths, never as text** — the AppCompat vector format
has no fonts, and the letters must not shift with a font substitution.

## Legibility floor

The icon is seen at 48 dp (app drawer), 72 dp (in-app About screen) and 192 dp
(variant picker).

- No feature smaller than **8 px** at 512, no gap thinner than **10 px**.
- IN letterform strokes: **18–26 px** at 512.
- Letter shapes: geometric, uppercase, single weight; the I may be a plain bar, the N
  may have a straight diagonal. Nothing decorative.

## Contrast — this is the part that changes

The current artwork is a white bubble on all 19 colours. Measured against WCAG
contrast, that is 1.40:1 on yellow, 1.51:1 on lime and 1.63:1 on amber — effectively
invisible. Adding IN to one fixed asset would inherit the same problem.

So: **two foreground assets**, identical geometry, differing only in ink.

- `light-ink` — near-black **#1A1A1A** (or darker) on the bubble and the IN.
- `dark-ink` — white **#FFFFFF** on the bubble and the IN.

Minimum **3:1** between ink and background for every variant. Measured, this splits as:

| Ink | Variants | Contrast range |
|---|---|---|
| `light-ink` (#1A1A1A) | Red #F44336, Blue #2196F3, Light blue #03A9F4, Cyan #00BCD4, Teal #009688, Green #4CAF50, Light green #8BC34A, Lime #CDDC39, Yellow #FDD835, Amber #FFC107, Orange #FF9800, Deep orange #FF5722 | 4.7 – 12.5 : 1 |
| `dark-ink` (#FFFFFF) | Pink #E91E63, Purple #9C27B0, Deep purple #673AB7, Indigo #3F51B5, Brown #795548, Blue grey #607D8B, Grey black #212121 | 4.0 – 16.1 : 1 |

If the IN is a knockout showing the background colour through the bubble, it is only
legible where the bubble–background contrast clears 3:1, which is exactly the split
above — do not use a knockout on the light-ink group.

## Monochrome layer (Android 13+ themed icons)

A third asset, from the same geometry:

- single solid silhouette, **transparent** background, IN included as part of it
- holes no smaller than **12 px**, bridges no thinner than **10 px**
- it gets tinted by the system, so it must read as a shape, not a colour illustration

## Deliverables

| # | File | Format | Size | Notes |
|---|---|---|---|---|
| 1 | `ic_launcher_foreground_light_ink.svg` | SVG, paths only | 512 × 512 | Bubble + IN in #1A1A1A |
| 2 | `ic_launcher_foreground_dark_ink.svg` | SVG, paths only | 512 × 512 | Bubble + IN in #FFFFFF |
| 3 | `ic_launcher_monochrome.svg` | SVG, paths only | 512 × 512 | Solid silhouette |
| 4 | `icon.svg` | SVG | 512 × 512 | Mark on the default green #4CAF50, for docs |
| 5 | `icon.webp` | WebP | 512 × 512 | Raster of #4, sRGB |
| 6 | `featureGraphic.png` | PNG | 1024 × 500 | Optional store/README banner |

SVG rules for 1–3: no `<text>`, no filters, no shadows, no gradients, no embedded
raster, no stroke-based outlines, no CSS classes; one fill colour per file.

## Legal note

The bubble is Fossify's artwork, kept under the same GPL-3.0 licence as the rest of the
upstream code. Adding IN makes it a modified version of their logo; their **name and
logo are their trademark**, which the GPL does not grant. The `NOTICE` file records the
modification and states that the app is neither affiliated with nor endorsed by
Fossify. If the app is ever published beyond personal use, a mark of its own is the
safer route.

---

## As delivered, and what was changed to integrate it

The artwork arrived as four SVGs with the mark drawn as two paths: the bubble with its two
lines as holes, and the IN as a separate **filled** path in the same ink colour. Because
the IN sits inside the bubble and is filled with the bubble's own colour, it renders
invisibly — the icon is a plain bubble. Checked by rasterising the files.

Integrated with three changes:

1. **IN is a hole now.** The bubble, its lines and the IN are one path with
   `fill-rule="evenodd"`, so the letters show the launcher's background colour instead of
   the bubble's ink. In the monochrome layer the same path gives a cut-out IN.
2. **IN scaled to 1.5×** — from a 48 px to a 72 px cap height, keeping its bottom-left
   anchor. At 48 px the letters are unreadable at the 48 dp size launchers use; at 72 px
   they read. Geometry after scaling: x 187–309, y 239–311, 10 px clear of the line above.
3. **Six variants flip ink, thirteen keep white.** The delivered artwork assumed white
   everywhere, which measures below 2.5:1 on six colours. Keeping white elsewhere
   preserves the look; only amber, cyan, light green, lime, orange and yellow draw the
   bubble in `#1A1A1A`.

`featureGraphic.png` still shows the previous mark without the IN.
