# News Shorts — design system

Two colours, taken from the logo. Everything else is those two hues at other
luminances plus a neutral ramp tinted toward the azure, so the greys read as
part of the brand rather than as something parked next to it.

The palette this replaced carried mint, coral, amber and violet at once, none of
which appeared in the mark. Four accents with no common origin is what makes a
screen look assembled rather than designed; the fix was fewer colours, not
better ones.

## Brand

| | hex | from |
|---|---|---|
| Azure | `#005291` | the logo's mark |
| Crimson | `#FF0043` | the logo's underline |

Azure appears at `#5FAAE8` on dark surfaces — the raw logo azure fails contrast
there. Crimson appears at `#C8003C` on light (the pure hue cannot carry white
text on white) and `#FF4D74` on dark. Same hues, different luminance; never
different colours.

## Role policy

This is the part that matters. Breaking it is how the old palette ended up with
`secondary` and `error` set to the same coral, which made every decorative
flourish in the app look like a warning.

| role | reads as | used for | never |
|---|---|---|---|
| `primary` | azure | navigation, tab indicator, buttons, links | decoration |
| `secondary` | azure-slate | icon tiles, supporting chrome, avatar placeholder | anything urgent |
| `tertiary` | crimson | **selected chips on dark**, saved articles | generic decoration |
| `error` | deep crimson | destructive actions, failures | anything else |

Azure carries navigation — where you are, and what you can press. Crimson
carries selection and saving — what you chose. Crimson stays under roughly 10%
of any screen; scarcity is what makes it read as deliberate.

### The selected chip changes colour by theme, on purpose

The selected chip's whole job is to be the most salient thing in its row, so it
takes whichever brand colour has the most presence on the surface underneath it:

| surface | selected chip |
|---|---|
| over imagery | crimson `#E4003D` |
| dark | crimson `tertiary` |
| light | azure `primary` |

This is not a shortcut. On dark, azure arrives as `#5FAAE8` — a pale tint that
sinks into blue-grey chrome, so selection stopped announcing itself and the
reader had to hunt for the active filter. Crimson wins the job back.

On light, azure is `#005291`: already the darkest, most saturated thing on a
near-white screen, and it wins outright. Crimson there buys no clarity and costs
real money — a saturated red block on white reads as a warning, and the same
screen carries a red delete affordance a few rows down. Two reds on one light
surface is precisely the ambiguity this palette exists to remove.

### Labels take neither accent

A category badge names the article's category; the chip row at the top of the
screen names the selected filter — and because the feed is filtered, they are
almost always the same word. Two identical labels a screen apart in two
different accent colours invited the reader to find a difference that is not
there, so the badge is neutral: dark glass on the feed, `surfaceVariant` on the
details screen.

## Colour values

Every pair below was measured. Body text clears 4.5:1, boundaries and icons
clear 3:1.

### Light

```
background       #F5F8FB    surface           #FFFFFF
surfaceVariant   #DFE8F1    surfaceContainer  #E9EFF6
onBackground     #0B1D2E    (16.03:1)   onSurfaceVariant  #4E6379  (5.82:1)
outline          #6E8598    (3.60:1)    outlineVariant    #D3DEE9
primary          #005291    (7.53:1)    onPrimary   #FFFFFF  (8.03:1 on primary)
primaryContainer #CFE2F5    onPrimaryContainer   #00304F  (10.32:1)
secondary        #4A6480    (5.75:1)    onSecondary #FFFFFF  (6.13:1)
secondaryContainer #D5E1EE  onSecondaryContainer #152C40  (10.80:1)
tertiary         #C8003C    (5.60:1)    onTertiary  #FFFFFF  (5.96:1)
tertiaryContainer #FFD9E1   onTertiaryContainer  #5C0019  (11.07:1)
error            #A00020    (7.83:1)    onError     #FFFFFF
errorContainer   #FFDAD9    onErrorContainer     #410007  (13.26:1)
```

### Dark

```
background       #081726    surface           #0F2234
surfaceVariant   #1B3247    surfaceContainer  #152B41
onBackground     #E6EEF7    (15.45:1)   onSurfaceVariant  #A0B6CA  (8.65:1)
outline          #6B8299    (4.55:1)    outlineVariant    #24405A
primary          #5FAAE8    (7.24:1)    onPrimary   #00243F  (6.35:1 on primary)
primaryContainer #00456F    onPrimaryContainer   #CFE2F5  (7.62:1)
secondary        #A9C2DC    (9.85:1)    onSecondary #152C40  (7.80:1)
secondaryContainer #32485F  onSecondaryContainer #D5E1EE  (7.11:1)
tertiary         #FF4D74    (5.66:1)    onTertiary  #4A0014  (5.05:1)
tertiaryContainer #8C0028   onTertiaryContainer  #FFD9E1  (7.58:1)
error            #FF8A9B    (8.06:1)    onError     #5C0019  (6.37:1)
errorContainer   #8C0014    onErrorContainer     #FFDAD9  (7.66:1)
```

Two candidates were rejected on measurement: `outline #8FA5B9` at 2.39:1, below
the 3:1 a boundary needs, and `onTertiary #5C0019` on dark at 4.47:1.

## `OnImagery` — the third surface

The feed is neither theme. It is text laid directly over photographs, which is
why it stays dark whatever the reader picked. That used to be expressed as about
thirty-five white-and-black literals across the card, header, chips and nav bar
— a palette nobody had written down. It now lives in `theme/OnImagery.kt`.

| token | value | note |
|---|---|---|
| `content` | white | headlines |
| `contentMuted` | white 72% | source, timestamps |
| `contentFaint` | white 55% | recedes until looked for |
| `fill` | white 10% | resting chip fill |
| `fillStrong` | scrim 80% | share/save buttons |
| `border` | white 22% | unselected chip hairline |
| `selectedFill` | `#E4003D` | selected chip; white on it is 4.81:1 |
| `savedTint` | `#FF4D74` | saved bookmark — the dark scheme's `tertiary`, so a saved article is the same crimson as a selected chip rather than a second, paler red |
| `ImageryScrim` | `#03121F` | azure-black, not `Color.Black` |

Two of those need their reasoning kept.

**`fillStrong` is dark, not a white wash.** As white at 22% it inherited
whatever was behind it, so a control over a snow-bright photograph became
pale-on-pale — a white icon on it measured **1.99:1**. Painting the chip dark
gives the icon a known background whatever the picture does. That is the only
reason a coloured state on it can be legible at all.

**`savedTint` is the dark scheme's crimson exactly.** The chip's own
`#E4003D` measures only 2.50:1 against `fillStrong` on a near-white photo and
would fail; `#FF4D74` reaches 5.86:1 on a typical photograph and 3.76:1 in that
worst case, clearing the 3:1 an icon needs while still reading as the same red.

**The scrim is `#03121F`, not black.** Same azure cast as the dark neutrals, so
the feed reads as this app's dark surface rather than an absence of one.

## Shape

Ten radii were in use before — buttons at 14 and 16, cards at 0, 12 and 16,
chips at 14, 16 and 24 — because the theme never passed a `Shapes` and every
call site picked its own. Two cards in the same list rounded differently.

```
extraSmall   8dp   tags, small pressable surfaces
small       12dp   buttons, text fields
medium      16dp   cards
large       20dp   large containers
extraLarge  28dp   sheets
PillShape   50%    chips, nav indicator
```

`PillShape` sits outside the five steps deliberately: M3 has no slot for it, and
a pill is a distinct idea rather than a sixth size.

## Type

One family per script, chosen from the reader's app language: **Tajawal** for
Arabic, **Poppins** for Latin. Both bundled in `composeResources/font/`, so a
headline weighs the same on Android, iOS and desktop. Every style previously
asked for `FontFamily.Default`, which meant the platform chose — and the Black
and ExtraBold steps often had no matching face to choose at all.

Poppins carries 100–700, Tajawal 300–800. Styles still name weights neither has
in common; Compose resolves to the nearest registered face, so each family lands
on its own heaviest cut rather than being synthetically smeared into one.

Two things differ for Arabic beyond the family, and neither is cosmetic:

**Tracking is forced to zero.** Arabic is a connected script and positive
letter-spacing pulls joined letters apart. The M3 scale ships tracking on nine of
its fifteen styles because it is designed around Latin.

**Leading opens up, unevenly.** Arabic carries dots and diacritics above and
below the baseline that Latin metrics leave no room for. Small text is where
lines actually collide, because the marks stay roughly the same size while the
leading shrinks with the font:

```
display / headline-large   ×1.12
headline / title           ×1.22
body / label               ×1.40
```

A stacked title-and-subtitle also needs real space between the two `Text`s —
Tajawal's metrics are tight enough that a bare `Column` collides. `SectionHeader`
and `SettingsEntryRow` use `Arrangement.spacedBy(3.dp)`.

## Components

One of each, replacing four hand-rolled variants apiece.

- **`FilterPill`** — categories, countries, news languages, theme modes. Was four
  implementations (a 24dp box, a 16dp two-line column, a 16dp box, a 14dp box)
  that disagreed on animation duration and on what "selected" looked like.
  Takes `onImagery` to decide where its colours come from.
- **`SelectorRow`** — scrolls the selection into view (with seven categories and
  thirteen countries the current choice was regularly off screen, and re-entering
  a tab always started at the first item), fades its own content at whichever
  edge still has more to show, and uses one spacing constant where the three rows
  previously used 8, 12 and 10dp. The fade is a `DstIn` mask over the content,
  not a gradient painted on top, because over the feed there is no single colour
  to fade into.
- **`AppButton`** — was 14dp/50dp, 16dp/54dp, 14dp/52dp and 16dp/52dp for one
  role. Now `shapes.small` at 52dp with `Primary`, `Secondary` and `Imagery`
  tones.

Countries used to be a row of ~110dp two-line tiles directly beneath the
masthead. The flag now sits inline where a category's emoji does, and the row is
the same height as any other.

## The launch window follows the app's Appearance, not the phone's

The first frame of a cold start is not drawn by the app. The system paints the
launch window from the activity theme's `windowBackground`, and it picks between
`values/colors.xml` and `values-night/colors.xml` using the *device's* dark-mode
flag — before any app code runs. A reader who picks Light on a phone set to Dark
therefore got a dark launch window in front of a light app. It only shows up on
a device whose system setting disagrees with the in-app one, which is why it
survived every emulator check until it was seen on a Galaxy S25 Ultra.

Compose cannot reach this frame. `ApplyAppNightMode` pins the app's night mode
with `UiModeManager.setApplicationNightMode` — the framework's own mechanism for
"this app has its own dark-mode switch" — so the system resolves the right
resources at the *next* launch. `MODE_NIGHT_AUTO` is the only unpinned value the
API offers, so that is what Automatic maps to.

Two details this depends on:

- It skips the first `themeMode` it is handed. `NewsUiState` starts at the
  `SYSTEM` default and only becomes the saved choice once `loadSavedSettings`
  returns, so pinning eagerly would unpin a saved Light or Dark on every launch.
- `MainActivity` declares `uiMode` in `android:configChanges`. Pinning changes
  the app's configuration; without this the Activity is recreated and the reader
  sees a flash every time they touch the Appearance setting.

Below API 31 there is no per-app night mode and no system splash screen either,
so a reader whose app and phone disagree still gets one wrong-coloured frame.

## Deliberately not done

**No per-category colour.** Category identity is carried by emoji and always has
been. A hue per category is precisely the rainbow that reads as machine-picked;
one selection treatment for all of them is the identity.

**Emoji flags stay.** They render inconsistently across platforms and not at all
on some JVM/Windows setups. Replacing them with vector assets is its own task.
