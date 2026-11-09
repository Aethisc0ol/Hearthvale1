# Cutscenes (heart events with camera work)

An event can be a plain dialogue `"script"` (still supported) **or** a `"cutscene"`: a list of steps that moves the camera,
walks NPCs, fades the screen, spawns extra actors, plays sounds and runs dialogue. **No extra mods are required** - the camera,
input lock, letterbox and fades are built on vanilla + NeoForge events.

```json
{
  "id": "rooftop", "min_hearts": 10, "status": ["dating"], "time": "evening",
  "cutscene": {
    "skippable": false,
    "steps": [
      { "do": "fade", "to": "black", "ticks": 20 },
      { "do": "title", "text": "That evening...", "ticks": 60 },
      { "do": "camera", "to": { "from": "npc", "offset": [0, 4, 6] }, "look": "npc", "snap": true },
      { "do": "fade", "to": "clear", "ticks": 20 },
      "A plain string is a dialogue line spoken by the NPC.",
      { "say": "A line with choices that branch the SCENE:", "choices": [
          { "text": "Option A", "reply": "NPC reply", "points": 50,
            "steps": [ { "do": "emote", "actor": "npc", "emote": "heart" } ] },
          { "text": "Option B", "reply": "Another reply" }
      ] },
      { "do": "camera", "reset": true }
    ]
  }
}
```

## What the player experiences
Controls lock (movement, mouse look, attacks, inventory), the HUD hides, black letterbox bars slide in, the player is immune to damage,
and the camera is yours. **ESC skips** (if `skippable`): all remaining effects (points, flags, gifts, commands) are applied instantly,
questions auto-pick their **first** choice, actors jump to their final positions. When the scene ends (or the player dies, logs out or changes
dimension) everything is released: NPCs resume their schedules, extras vanish, the player is returned to where they started.

## Cutscene options
| Key | Default | Meaning |
|---|---|---|
| `skippable` | `true` unless the scene contains choices | allow ESC to skip. Put the "expected" choice first, or set false |
| `origin` | `"npc"` | what `[x,y,z]` offsets are relative to: `"npc"`, `"player"`, `"hearth"`, `"building"` (the building the NPC is in), or absolute `[x,y,z]` |
| `player_start` | none | warp the player here at the start |
| `return_player` | `true` | return the player to their start position afterwards if the scene moved them |
| `restore_npcs` | `true` | return borrowed NPCs to where they were |
| `marks` | `{}` | named spots: `"marks": {"counter": [2, 0, 1]}` then `"to": "counter"` |
| `actors` | `{}` | aliases for other NPCs: `"actors": {"garrick": "hearthvale:garrick"}`. The real NPC is borrowed if present and free; otherwise a look-alike stand-in is spawned so the scene still plays. Add `"requires_npc": ["hearthvale:garrick"]` to the event if you'd rather wait for the real one |
| `steps` | - | the list below |

## Positions
Anywhere a position is expected you can give:
- `[dx, dy, dz]` - offset from `origin`
- `"player"`, `"npc"`, an actor name, or a mark name - that thing's current position
- `{ "from": "player", "offset": [2, 0, 0] }` - offset from any of the above
- `{ "abs": [x, y, z] }` - absolute world coordinates

## Steps
Every step is an object with `"do"`. Steps run **in order**; the ones marked (waits) pause the scene until they finish. Add `"wait": false` to
run a step in the background (e.g. an NPC walks while the camera pans) and a `{ "do": "sync" }` step to wait for all background steps.

| `do` | Fields | Notes |
|---|---|---|
| *(dialogue)* | `say`, `speaker`, `choices`, `points`, `flag`, `give`, `command`... | same line format as normal scripts. A bare string works too. Consecutive lines form one conversation. A line whose choices have `steps` branches the scene (and ends that conversation). **Waits** |
| `wait` | `ticks` or `seconds` | **waits** |
| `sync` | - | waits for background steps |
| `fade` | `to` (`black`/`white`/`clear`/`"#RRGGBB"`), `ticks` | **waits** (default 20 ticks) |
| `letterbox` | `on` | bars are on by default |
| `title` | `text`, `ticks` | centred title card. **Waits** |
| `camera` | `to` (position), `look`, `ticks`, `ease` (`smooth`/`linear`), `snap`; or `reset: true` | `look`: an actor name (tracks it), a position, or `{"yaw":..,"pitch":..}`. Omit `look` to keep the current aim. `reset` gives the view back to the player. Keep the camera within ~30 blocks of the player (chunks load around the player). **Waits** |
| `move` | `actor`, `to`, `speed`, `within` | NPCs path-find (opening doors; teleport if stuck). The **player walks in a straight line** (their position is driven by the server, so keep the path clear). `within` = stop this close. **Waits** |
| `warp` | `actor`, `to`, `face` | instant teleport (NPCs land on the nearest safe floor) |
| `face` | `actor`, `toward` (position/actor) or `direction` (`north/south/east/west`) | |
| `emote` | `actor`, `emote` (`heart`, `happy`, `angry`, `music`, `sad`, `exclaim`, `question`, `sweat`) | particles above the head |
| `sound` | `sound`, `at`, `volume`, `pitch` | any sound event id |
| `animate` | `actor`, `anim` (`swing`, `hurt`, `jump`, `crouch`), `ticks` | |
| `hold` | `actor`, `item` (`"none"` clears) | NPCs visibly hold the item; restored after the scene |
| `spawn` | `actor` (a name you choose), `name`, `skin`, `at`, `face` | temporary extra using any NPC skin (`"hearthvale:bram"`) or a bare skin path (`"mypack:crowd_1"` -> `assets/mypack/textures/entity/npc/crowd_1.png`). Removed automatically |
| `despawn` | `actor` | |
| `effect` | `points`, `flag`, `give`, `command`, `settle`, `marry`, `date`, `engage`, `effects` | same effect shorthand as dialogue lines; `command` runs at op level as the player, e.g. `particle minecraft:end_rod ~ ~2 ~ 3 1 3 0.01 50` |
| `if` | `if` (any [event conditions](ADDON_GUIDE.md)), `then`, `else` | |
| `end` | - | stop the scene early |

The actor names `"player"` and `"npc"` (the NPC the event belongs to) always exist.
Speakers: a line's `speaker` shows that character's portrait if the name or id matches an NPC; any other speaker (e.g. `"Narration"`) shows no portrait.

## Tips
- Offsets are in **world axes** (not rotated with the building), so keep walks short or follow actors (`"to": "player"`) instead of hard-coded paths.
- Cameras can sit inside walls - handy for dollhouse shots of a room.
- Test with `/hearthvale event <npc> <event_id>` (op) next to the NPC; `/hearthvale hearts` and `/hearthvale status` set up prerequisites.
- Mistakes are logged at load time ("Failed to load NPC ...") or when the step runs ("Cutscene step 'x' failed"); a failing step is skipped, not fatal.
