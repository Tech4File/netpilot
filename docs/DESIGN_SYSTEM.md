# NetPilot Design System

A compact, token-driven system built for **10-foot UI** that scales down gracefully to phones.
Everything below is defined once in `res/values*` and the reusable components in
`app.netpilot.ui.components`.

## Color palette (light · dark)

| Token | Light | Dark | Used for |
|---|---|---|---|
| `brand_primary` | `#3D4DE0` | `#93A0FF` | primary actions, active nav, focus ring |
| `brand_on_primary` | white | `#0C1152` | text on primary |
| `brand_primary_container` | `#E2E5FF` | `#252E7A` | selected segments, icon badges |
| `brand_secondary` | `#00A896` | `#3EDBC4` | VPN accents |
| `brand_tertiary` | `#E8871E` | `#FFB65C` | highlights |
| `bg` | `#F4F5FA` | `#0B0E17` | window background |
| `surface` / `surface_variant` | white / `#EAECF4` | `#151A2A` / `#1D2337` | cards, chips |
| `on_surface` / `on_surface_variant` | `#171A26` / `#575D72` | `#E8EAF4` / `#A7ADC2` | text hierarchy |
| `status_success` | `#148A54` | `#63D795` | protected / connected |
| `status_info` | `#2559C9` | `#AEC5FF` | partial protection |
| `status_warning` | `#B26200` | `#FFC26B` | unprotected, advisories |
| `status_danger` | `#C62F45` | `#FF97A3` | destructive actions |
| `focus_ring` | `#3D4DE0` | `#AEB9FF` | D-pad focus stroke |

Dark mode is the default persona (TVs live in dim rooms): deep space navy, OLED-friendly,
AA-contrast minimum for all text.

## Type scale
| Style | Size | Weight |
|---|---|---|
| Display (hero status) | 30 sp | bold |
| Title (screen/section) | 21 sp | bold |
| Body | 15 sp | regular |
| Small | 13 sp | regular |
| Label (overlines) | 11 sp | bold, all-caps, +12% tracking |

## Spacing & shape
- 4-pt grid: `4/8/12/14/16/20/24/32 dp`
- Cards: 22 dp radius, 18 dp padding, ≥72 dp min height
- Pills: 26 dp height; status dot 9 dp; minimum touch target 48 dp

## Components (all reusable, all D-pad ready)

### FocusCardView
The workhorse container. On TV: 2 dp focus ring + `translationZ` lift + 1.05 zoom on focus;
sub-second 120 ms animation. On touch: standard Material ripple card.

### NavRailView (TV only)
Left rail: brand header, 4 focusable items (icon+label), version footer. Active item = tinted
pill (`bg_nav_item`) + brand-colored icon/text; focused item zooms subtly. Back from content
focuses the rail.

### SegmentedToggle
Row of equal-width FocusCards for mode selection (Off/Automatic/Profiles; IKEv2/OpenVPN).
Focus moves highlight; Enter/OK commits; selected = `primary_container` + primary label.

### StatusPill
Dot + label on a 26 dp tinted capsule. Semantic colors only (`status_*`).

### SettingRow
Icon + title (+ subtitle) + trailing value + chevron on a FocusCard. Used across Settings.

### EmptyStateView
Centered icon/title/body pair for empty lists, with D-pad-friendly "add" actions next to it.

## TV interaction rules (docs/TV_NAVIGATION.md has the full map)
1. Every interactive element is focusable and ≥48 dp.
2. Focus is unmistakable: ring + lift (+ zoom on TV).
3. Left arrow from list content exits to the rail (`nextFocusLeft="@id/nav_rail"`).
4. Enter on a focused DNS/VPN row activates it; edit/delete are separate right-side actions.
5. Back collapses intent (Dialog → screen → Dashboard → exit).

## Assets
- `drawable/tv_banner.png` — 320×180 TV banner (AI-generated brand art, see `art/`)
- Adaptive launcher icon: vector foreground (`ic_launcher_foreground`) + generated raster mipmaps
  + monochrome layer for themed icons (Android 13+)
- All icons are first-party Material-style vector paths in this repo — no icon fonts, no downloads.
