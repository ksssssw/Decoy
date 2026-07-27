# Decoy Design System

The design language of the Decoy inspector web UI (`decoy-android/src/main/resources/decoy-web/index.html`).
Everything in this document applies to that single self-contained page; there is no other UI surface.

## Principles

1. **Instrument, not dashboard.** Decoy is a measuring tool. The chrome stays quiet and dense;
   the captured data is the interface. Color is reserved for semantics (status, method, mock,
   danger) — never for decoration.
2. **Mono carries the data.** Every runtime value — URLs, methods, status codes, durations,
   headers, JSON — renders in the monospace stack. The sans stack is only for chrome labels
   (tabs, buttons, section titles). If you can copy-paste it into a terminal, it is mono.
3. **Solid is real, dashed is decoy.** The signature visual grammar: anything that actually
   happened on the network uses solid strokes; anything synthetic — mocked responses, mock
   rules, drop-target previews that *would* create something — uses **dashed strokes in the
   decoy purple role**. One glance separates truth from decoy.
4. **Every state is visible.** All interactive elements define hover, active, focus-visible,
   and disabled states. Keyboard focus always shows a 2px ring (`--focus-ring`). Nothing
   changes state invisibly.
5. **Self-contained forever.** No external assets: no webfonts, no CDN, no remote images.
   System font stacks, self-drawn inline SVG icons, inline CSS/JS in one file. The page must
   work fully offline on a loopback socket.

## Foundations

### Color roles

Colors are defined once as CSS custom properties on `:root` (dark, the default) and overridden
in `:root[data-theme="light"]`. Components reference roles, never raw hex.

| Role | Dark | Light | Use |
|---|---|---|---|
| `--bg-app` | `#0e1116` | `#f4f5f8` | Page background |
| `--bg-inset` | `#0a0c10` | `#fbfcfe` | Sunken areas: code viewers, editors, trees |
| `--bg-panel` | `#151a21` | `#ffffff` | Panels, header, cards |
| `--bg-subtle` | `#1b212a` | `#f1f3f7` | Bars, table heads, secondary surfaces |
| `--bg-hover` | `#232a35` | `#e9ecf2` | Hover fills |
| `--bg-active` | `#2c3441` | `#dee2ea` | Active/pressed fills, scrollbar thumbs |
| `--border-default` | `#262d38` | `#dfe3ea` | Hairline separators |
| `--border-emphasis` | `#3d4655` | `#c3c9d4` | Input borders, strong separators |
| `--fg-default` | `#e6e9ef` | `#1f242e` | Primary text |
| `--fg-muted` | `#b9c0cd` | `#454c5c` | Secondary text |
| `--fg-subtle` | `#858e9d` | `#67707f` | Tertiary text, placeholders, icons at rest |
| `--accent-fg` | `#9db9ff` | `#2456c4` | Accent foreground: links, active tab text, brand |
| `--accent-emphasis` | `#508ff8` | `#3567de` | Accent fills: primary buttons, switches, focus |
| `--on-accent` | `#04122b` | `#ffffff` | Text on accent fills |
| `--decoy-fg` | `#b79cff` | `#5b32d6` | Decoy purple, foreground (MOCK tags, mocked durations) |
| `--decoy-emphasis` | `#7a52f0` | `#6a3df0` | Decoy purple, fills and rails |
| `--success-fg` | `#4ade80` | `#15803d` | 2xx, enabled, connected |
| `--attention-fg` | `#e3b341` | `#9a6a03` | 3xx, warnings |
| `--severe-fg` | `#f0883e` | `#c2410c` | 4xx |
| `--danger-fg` | `#ff8a80` | `#cc3340` | 5xx, errors, destructive actions |
| `--syntax-key` | `#a5c0f5` | `#3b5bb5` | JSON keys |
| `--syntax-str` | `#ffb383` | `#a04d00` | JSON strings |
| `--syntax-num` | `#9db9ff` | `#2456c4` | JSON numbers |
| `--syntax-bool` | `#7ee2a8` | `#15803d` | JSON booleans / null |
| `--shadow-color` | `rgba(0,0,0,.5)` | `rgba(25,32,50,.16)` | Shadow base |
| `--scrim` | `rgba(8,10,14,.7)` | `rgba(35,40,55,.45)` | Modal backdrop |

HTTP method hues (applied through the method badge component, derived with `color-mix` from
the roles above): GET → accent, POST → success, PUT → attention, PATCH → severe, DELETE →
danger, other → subtle.

### Typography

```css
--font-mono: ui-monospace, "SF Mono", SFMono-Regular, Menlo, "Cascadia Code",
             "Segoe UI Mono", Consolas, "Liberation Mono", monospace;
--font-sans: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
```

System stacks only — zero bytes shipped, zero licensing exposure. Embedding an open-licensed
webfont was considered and rejected: it would add ~200 KB of base64 to the AAR and a license
notice obligation, for marginal gain on a page whose only realistic client is a desktop browser
with excellent system monospace fonts.

Type scale (no text below 10px, ever):

| Token | Size | Use |
|---|---|---|
| `--text-2xs` | 10px | Micro labels, hints, meta captions |
| `--text-xs` | 11px | Table cells, badges, secondary mono values |
| `--text-sm` | 12px | Body mono (code, editors), buttons, toasts |
| `--text-md` | 13px | Inputs, primary list text, modal titles |
| `--text-base` | 14px | Page base |
| `--text-lg` | 16px | Rarely — emphasized values |
| `--text-xl` | 20px | View headings |
| `--text-2xl` | 24px | Reserved |

Casing rules: UPPERCASE is allowed only for section headers, tab labels, and micro-caption
labels, with `letter-spacing` never above 0.5px. Everywhere else use sentence case and create
hierarchy with weight and `--fg-muted`/`--fg-subtle`, not tracking.

### Spacing

4px base grid: `--space-1: 4px` through `--space-8: 32px` in 4px steps.
Component-internal padding sticks to the grid; odd values are not introduced without reason.

### Radius

| Token | Value | Use |
|---|---|---|
| `--radius-sm` | 3px | Badges, chips, quick-code buttons |
| `--radius-md` | 6px | Buttons, inputs, editors, status chip |
| `--radius-lg` | 10px | Modals, cards |
| `--radius-full` | 999px | Switches, dots |

Decoy leans square. Pills are reserved for switches and dots; data chips are near-square
(`--radius-sm`) so they align into scannable columns.

### Elevation

| Token | Use |
|---|---|
| `--shadow-sm` | Status chip, small floating elements |
| `--shadow-md` | Toasts, dropdowns |
| `--shadow-lg` | Modals |

Elevation is used only for elements that float above the layout. Panels in the layout are
separated by borders, not shadows.

### Motion

| Token | Value |
|---|---|
| `--duration-fast` | 120ms — hovers, color changes |
| `--duration-base` | 180ms — entrances (views, modals, toasts) |
| `--ease-out` | `cubic-bezier(.2, .8, .3, 1)` |

Motion is entrance-only and subtle: fades and ≤6px translates. No bouncy overshoot, no
attention-seeking loops except the connection-status pulse. Theme switching cross-fades all
colors over 250ms.

### Focus

```css
--focus-ring: 0 0 0 2px color-mix(in srgb, var(--accent-emphasis) 45%, transparent);
```

Applied via `:focus-visible` to every interactive element (buttons, inputs, selects,
textareas, switches, tabs, rows, resize handles). Never remove an outline without replacing
it with the ring.

## Components

**Buttons.** Sentence case, `--text-sm`/600, `--radius-md`, padding `6px 12px`.
Variants: *primary* (`--accent-emphasis` fill, `--on-accent` text), *outline* (1px
`--border-emphasis`, transparent fill, hover `--bg-hover`), *ghost* (`--bg-hover` fill on
hover only), *text* (bare, `--fg-subtle` → `--fg-default`), *icon* (32×32 hit area,
`--radius-md`), *danger* (text/outline in `--danger-fg`). Disabled: 45% opacity, no pointer.

**Text inputs & selects.** Boxed: 1px `--border-emphasis` border, `--bg-inset` fill,
`--radius-md`, padding `6px 10px`, `--text-md`. Focus swaps the border to `--accent-emphasis`
and adds the ring. Field labels: `--text-2xs`, 600, `--fg-subtle`, sentence case.

**Range slider.** 4px track in `--bg-active`, 14px thumb in `--accent-emphasis`, mono value
readout beside the label.

**Switch.** 32×16 pill. Off: `--bg-active` track, `--fg-subtle` thumb outline. On:
`--accent-emphasis` track. Mixed (tri-state group/master switches): thumb centered, track in
`color-mix(accent 40%)`. Motion: thumb slides in `--duration-fast`.

**Method badge.** Fixed-width (52px) mono chip, `--text-2xs`/700, `--radius-sm`, centered,
tinted background (`color-mix(hue 14%, transparent)`) with hue-colored text. Fixed width makes
methods a scannable column in dense lists.

**Tags.** `MOCK`: dashed 1px `--decoy-emphasis` border, `--decoy-fg` text, transparent fill —
the dashed-decoy grammar. `ERR`: solid `--danger-fg` tint. `DUP`: solid `--attention-fg` tint.

**Tabs.** Top-level: `--text-sm`/600, uppercase allowed, active = `--accent-fg` text + 2px
bottom border in `--accent-emphasis`. Detail sub-tabs: same grammar, smaller.

**Panel bars.** 38px, `--bg-subtle`, bottom hairline, mono counts in `--fg-subtle`.

**KV table (headers).** Mono `--text-xs`, key column `--fg-subtle` at 38% width, hairline row
separators, count pill in the section head.

**Code viewer.** `--bg-inset`, line-number gutter in `--fg-subtle`, JSON tokens in the
`--syntax-*` roles, `JSON`/`RAW` format pill, truncation banner in `--attention-fg` when the
server flags a clipped body.

**Payload editor.** An editor chrome bar (label + actions like *Format*) over a mono textarea
on `--bg-inset`. No decorative window dots. Body editor min-height 240px, headers editor
min-height 100px; both user-resizable vertically and the body editor grows to absorb free
modal height.

**Modal.** Card on `--bg-panel`, `--radius-lg`, `--shadow-lg`, over `--scrim` with blur.
Fixed header (title + maximize + close) and fixed footer; the form body is the scroll
container, so primary actions never scroll away. The rule modal is wide
(`min(920px, 100vw − 48px)` × `min(780px, 92vh)`), two-column at ≥960px (config left,
payload editors right), and resizable: a maximize toggle in the header and a drag grip in the
bottom-right corner (clamped 560×420 → viewport − 32px; size resets on close). Small modals
(import 440px, export 480px) are not resizable. Esc and scrim-click close.

**Toast.** Bottom-right, mono `--text-sm`, `--shadow-md`. Transient (auto-dismiss ~2.2s) or
action variant with an inline accent button (e.g. *Undo*).

**Status chip.** Fixed bottom-right: pulsing dot (`--success-fg` connected /
`--danger-fg` reconnecting), label + port in mono.

**Empty states.** `--fg-subtle`, one short line of guidance, optionally one hint line
(e.g. "Waiting for requests — make an HTTP call in the app").

## Patterns

**Brand mark.** The `DECOY` wordmark set in `--font-mono` 13px/700 `--accent-fg`, preceded by
the *signal fork* glyph: a self-drawn inline SVG of a horizontal line that forks into a solid
branch and a dashed branch — the real-vs-decoy grammar in miniature. The dashed branch uses
`--decoy-fg`.

**Traffic row.** `[method chip] [tags] [status] / path?query / [time] [duration]` — all mono.
Mocked rows carry a 2px dashed `--decoy-emphasis` left rail and a purple duration. New rows
flash once with an accent-tinted background fade.

**Rule row.** `[grip] [n] [method chip] [pattern] [code] [delay] [switch] [actions]` on a
7-column grid. Enabled rules carry the dashed decoy left rail; disabled rows dim to 45% with
an italic pattern.

**Group header.** Chevron (collapse), name, rename affordance, export action, `n/m on` meta,
tri-state switch. Hidden entirely when only one unnamed block exists.

**Drag & drop vocabulary.** *Insertion*: 2px solid `--accent-emphasis` line above/below the
target. *Combine* (dropping one ungrouped rule onto another to form a group): dashed
`--decoy-emphasis` outline on the target — dashed because the group doesn't exist yet.
*Drop-into-group*: tinted group header. Dragged source: 35% opacity.

**Resizable panel.** The traffic sidebar has a col-resize handle between it and the detail
pane: an invisible 10px hit area over a 1px divider that highlights to 2px `--accent-emphasis`
on hover/drag. Width is clamped to 240px → min(640px, 60vw), persisted to `localStorage`,
double-click resets to the 320px default. Disabled below 768px where the sidebar is full-width.

## Responsiveness

One breakpoint: **767px**. Below it the sidebar goes full-width, the detail pane becomes a
full-screen overlay with a back button, meta grids drop to two columns, form grids stack, and
panel/modal resizing is disabled. The rule modal's two-column layout additionally requires
≥960px. Everything must remain usable at 360px wide.

## Accessibility

- Text contrast ≥ 4.5:1 against its background; UI glyphs and large text ≥ 3:1 — in both themes.
- Minimum text size 10px; nothing smaller ships.
- `:focus-visible` ring on every interactive element; full keyboard path for modal open → edit
  → save/cancel.
- Hit targets ≥ 24×24px (icon buttons 32×32).
- Motion is short and non-essential; no information is conveyed by motion alone.
- Color is never the only signal: mock/error/duplicate states pair color with tags, dashes,
  or text.

## Licensing

- Fonts: system stacks only. No font files are shipped or referenced.
- Icons: self-drawn inline SVG paths (stroke-based, 16×16 grid). No icon fonts or third-party
  icon sets.
- No third-party CSS/JS. The page has zero external requests by design.

## Future (not yet implemented)

Rules-view search, arrow-key traffic navigation, styled confirm dialogs replacing native
`confirm()`, line numbers in the payload editor.
