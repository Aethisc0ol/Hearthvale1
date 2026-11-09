# Hearthvale - a Stardew-style village & romance mod (NeoForge 1.21.1)

Found a village, build rooms, and wanderers will arrive, settle in your houses, follow daily schedules,
befriend you, trigger heart events, and (if romanceable) date and marry you.
All NPCs, buildings, dialogue and events are **JSON data** - addons need no Java.

## Building the mod
1. Install JDK 21 and IntelliJ IDEA (or any Gradle-capable IDE).
2. Easiest: download the official **NeoForge MDK for 1.21.1**, then copy this project's `src/` folder over the MDK's
   `src/` (and use `gradle.properties` values from here). Or use this folder directly - it has a ModDevGradle `build.gradle`
   (run `gradle wrapper` once to generate the wrapper).
3. `./gradlew runClient` to test, `./gradlew build` for the jar in `build/libs`.

## Quick start (in game)
1. Craft/spawn a **Village Hearth** (stone bricks / campfire / bricks) and place it. That founds a village.
2. Build a closed room (walls + roof, doors, beds...). Stand inside and use a **Surveyor's Deed** (paper + ink sac).
   The mod flood-fills the room, counts the furniture and tells you what it detected - or exactly what's missing.
3. Every few minutes (config: `arrivalIntervalTicks`) a wanderer whose requirements are met walks in from the road to the hearth.
   **Right-click** them to welcome them; they move into a free home and start their schedule.
4. Talk to them daily, gift them things, and walk up to them to trigger heart events.

### Bundled building types
| Type | Needs (inside or touching the room) |
|---|---|
| Home | 1 bed + 1 door (sleeps = number of beds) |
| Library | 6 bookshelves + lectern + door |
| Blacksmith | anvil + furnace/blast furnace + smithing table/grindstone + door |
| Tavern | 2 barrels + campfire/smoker/cauldron + door |
| Apothecary | brewing stand + cauldron + 2 flower pots + door |
| General Store | 3 chests + sign + door |

### Bundled NPCs
Elara Voss (librarian, romanceable, 6 events), Garrick Ironhand (blacksmith, romanceable, 6 events),
Bram Hollis (tavern keeper, 4 events), Marta Quillfeather (apothecary, 4 events). They arrive once their building + a free home exist.
Skins are placeholders - drop real 64x64 skins over `assets/hearthvale/textures/entity/npc/*.png`.

### Controls
| Action | How |
|---|---|
| Talk (+20 points once/day) | right-click |
| Gift (max 1/day, 2/week) | **sneak** + right-click holding the item |
| Hug/kiss (dating+, +10/day) | sneak + right-click, empty hand |
| Ask to date (needs 8 hearts) | right-click holding a **Bouquet** (3 small flowers + string) |
| Propose (dating, 10 hearts) | right-click holding a **Promise Pendant** (4 gold + diamond) -> wedding in 3 days |
| Social overview | **Village Journal** (book + red dye) or `/hearthvale social` |

Friendship: 250 points = 1 heart. Romanceable NPCs stop at 8 hearts until you date them (10), marriage allows 14.
Loved +80, liked +45, neutral +20, disliked -20, hated -40; birthdays x8 for loved/liked.
The calendar is Stardew-like: day 0 = Monday, 7-day weeks, 28-day seasons.
After the wedding the spouse moves into the home containing your bed (set your spawn there) and uses their `spouse_schedule`.

### Commands
`/hearthvale social | village | list` for everyone.
Op-only (testing): `hearts <npc> <points>`, `status <npc> <friend|dating|engaged|married>`, `arrive <npc>`, `event <npc> <event_id>`.

### Config (`hearthvale-common.toml`)
`arrivalIntervalTicks`, `maxBuildingVolume`, `maxBuildingRadius`, `villageRadius`, `minVillageDistance`.

## The Hearthvale Book (in-game guide)
Press **J** (rebindable under Controls > Hearthvale) or right-click the **Village Journal** to open a pixel-art open book:
- **Front page** - tree logo on the left, contents on the right.
- **Villagers** - every villager as a collapsible card with their face and friendship counter. The counter counts up in stages:
  **0/8** while you get to know someone, **8/10** once you are dating, **10/14** when engaged or married (non-romanceable villagers: x/10).
  Open a profile for their summary and background, the exact rooms they need (block icons with `1X`, `2X`... counts), and their events.
  Click an event for its exact requirements (hearts, relationship, time, weather, location, earlier events...).
- **Items & Blocks** - each item with its description on the left and its recipe in a crafting grid on the right.
- **Settings** - book size and page-turn animation (this computer), plus village settings (arrival time, heart events auto-start, largest room size) that operators / the single-player owner can change.

**Navigation:** a **Back** button sits top-left on every deeper page (also Backspace or right-click). The two **brown tabs** on top jump to Villagers / Items & Blocks.
The **coloured bookmarks** on the right edge are: red = front page, green = settings, purple = close. Pages turn like a book. Text always wraps; long pages scroll with the mouse wheel.
**Size:** the book is drawn at a whole number of screen pixels per art pixel so it stays crisp at any GUI scale. *Auto* picks the biggest that fits;
choose Small / Medium / Large / Huge in Settings (or `uiSize` in `hearthvale-client.toml`).

**Adding a bookmark later:** in `BookScreen`'s constructor add one line - `bookmarks.add(new Bookmark(spriteTexture, "Tooltip", action, isActive))` - and drop a 25x17 flag PNG next to the existing `flag_*.png`.

**Dialogue** uses the same pixel panel: framed portrait (currently the villager's face - mood sprites come later), name tab, friendship counter, tan choice cards, and the same font.

**Font:** everything uses *Game Paused DEMO* (`assets/hearthvale/font/game_paused.otf`). That demo font contains **only A-Z and a-z**, so the mod ships a small companion
pixel font (`font/pixel_extras.png`) for digits and punctuation, and for a plain capital **E** (the demo's own E looks like a C - delete the `E` from the bitmap
chars in `font/fantasy.json` if you prefer the original). **Licence:** Game Paused DEMO is *free for personal use only* - it may not be redistributed or published
(not even inside a mod) without the author's written permission, and not used commercially. Keep this mod private, or get a licence / swap the font before sharing it.

**Art:** the book is built from your reference image (cover, page edges, spine, bookmarks) - see `textures/gui/book/`. Item, block, heart and tree textures are softened,
hand-generated pixel art you can replace (`textures/item`, `textures/block`, `textures/gui/book`).

## Cutscenes
Heart events can be full cutscenes: letterbox bars, camera moves, fades, title cards, NPCs walking/emoting/holding items,
temporary extra actors, sounds, and choices that branch the scene. Controls lock while one plays, ESC skips (if allowed).
Elara's *Lantern Night*, Bram's *Last Call*, Garrick's *Anvil Song* and others are examples. **No dependency mods needed.**
See **docs/CUTSCENE_GUIDE.md**.

## Adding your own NPCs / buildings (no Java)
See **docs/ADDON_GUIDE.md** and the working sample in **example_addon/** (a datapack for the JSON + a resource pack for the skin).
