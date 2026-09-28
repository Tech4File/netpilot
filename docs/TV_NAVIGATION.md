# TV Navigation Map (D-pad)

NetPilot assumes a remote: D-pad (↑↓←→), Center/OK, Back. No touch required anywhere.

## Global model
- **TV**: left navigation rail (Dashboard · Private DNS · VPN · Settings) is always one ← press
  away from any list. Content sits right of the rail.
- **Phone/tablet**: identical screens, bottom navigation, standard touch.

## Screen by screen

### Rail
- ↑/↓ moves between items; OK selects the section; selection shows tinted pill + bold label.
- → from the rail enters the current section's first card.
- The rail highlights on focus with a subtle zoom (focus is never ambiguous).

### Dashboard
```
[ Hero status card ]      ← focus starts here
[ DNS quick ][ VPN quick ]
[ Setup card (if needed) ]
[ Advisory (if both on)   ]
[ Network facts           ]
```
- ↑/↓ walks the stack; ← from a quick card returns toward the rail; OK on hero/DNS/VPN cards jumps
  to that section. Back returns to Dashboard from any section.

### Private DNS
- Header card: OK toggles the master switch (confirmation dialog on off).
- ←/→ moves between the mode segments; OK commits (Off / Automatic / Profiles).
- Profile rows: OK activates the profile (previous one auto-deactivates); the row's
  edit ✎ and delete 🗑 buttons are to the right; ← from a row goes toward the rail.
- Add-profile row is last in the list; the dialog opens with focus on the name field;
  on-screen keyboards are fully D-pad operable.

### VPN
- Header card switch: OK connects the first profile / disconnects.
- Rows: OK connects (replaces any active VPN — Android's single-VPN rule) or disconnects the
  active one; type badge (IKEv2/OpenVPN) and state are always visible.
- Dialog: segmented type switch first; import buttons and fields follow; PSK + CA import for
  IKEv2 user/pass auth.

### Settings
- Rows: Theme (dialog with radio list), Security (setup guide), Export, Import, Privacy,
  Licenses (opens attribution screen), About (version/device dialog).

## Back semantics
Dialog → dismisses · Guide → back to caller · Any tab → Dashboard → (rail focus on TV, else exit).

## Focus engineering notes
- `FocusCardView` renders focus as ring + elevation (+ zoom on TV) — visible from a couch.
- `android:nextFocusLeft="@id/nav_rail"` is set on full-width rows so ← always escapes to the rail.
- Lists are RecyclerViews with `nestedScrollingEnabled=false` inside scrollable screens so the
  system's focus search scrolls correctly.
