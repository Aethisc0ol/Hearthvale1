# Hearthvale addon guide

An addon is just files. Put them in a **datapack** (JSON) and a **resource pack** (skin), or inside any mod jar.
Reload with `/reload` and use `/hearthvale list` to confirm they loaded. Errors appear in `latest.log` as "Failed to load NPC ...".

```
data/<your_namespace>/hearthvale/npcs/<name>.json         -> NPC id  <your_namespace>:<name>
data/<your_namespace>/hearthvale/buildings/<name>.json    -> building type id <your_namespace>:<name>
assets/<your_namespace>/textures/entity/npc/<name>.png    -> 64x64 skin for that NPC
```
Ids without a namespace mean `hearthvale:` (so `"workplace": "library"` = `hearthvale:library`).
Overriding a bundled NPC: use the same path (`data/hearthvale/hearthvale/npcs/elara.json`) in a datapack.
Pack formats for 1.21.1: datapack 48, resource pack 34.

## Building type JSON
```json
{
  "display_name": "Fishing Hut",
  "priority": 40,            // higher wins when a room matches several types
  "min_volume": 8,           // open blocks inside the room
  "max_volume": 100000,
  "housing": false,          // true = NPCs can live here; capacity = number of beds
  "requires": [
    { "any": ["minecraft:barrel", "#minecraft:beds"], "count": 2 }   // block ids and/or #block_tags, total count
  ]
}
```
Blocks inside the room **and touching it** (walls, doors, furniture against walls) are counted. Beds and doors count once.

## NPC JSON
| Key | Meaning |
|---|---|
| `name` | display name |
| `slim` | true = 3px "Alex" arms |
| `romanceable` | enables bouquet / pendant / marriage |
| `profile` | `{"summary": "...", "backstory": "..."}` shown in the Hearthvale book. Keep anything revealed by events/dialogue OUT of it |
| `birthday` | `{"season": "spring\|summer\|fall\|winter", "day": 1-28}` |
| `home` | building type they live in (`"none"` = homeless). Default `home` |
| `workplace` | building type used by `"at": "work"` |
| `requires` | building types that must exist in the village before they will arrive |
| `schedule` | list of slots, **first match wins** (put rain/weekend special cases first) |
| `spouse_schedule` | schedule used once married to a player |
| `gifts` | `loved / liked / disliked / hated`: item ids or `#item_tags` |
| `gift_reactions` | `loved / liked / neutral / disliked / hated`: list of lines (`{item}` = item name) |
| `arrival` | script played when you first talk to the wanderer (finishing it settles them) |
| `bouquet_accept`, `bouquet_reject`, `pendant_accept`, `pendant_reject`, `wedding` | optional scripts; sensible defaults exist |
| `greetings` | list of one-line greetings with optional conditions |
| `events` | heart events (see below) |

### Schedule slot
```json
{ "from": "8:00", "to": "18:00", "at": "work", "days": ["sat","sun"], "weather": "rain" }
```
Times are Minecraft clock times (6:00 = sunrise = tick 0, 18:00 = sunset). Ranges may wrap past midnight.
`at`: `home` | `work` | `hearth` (idle near the hearth) | `wander` (roam ~14 blocks around the hearth) | `building:<type>` (any building of that type).
Each NPC walks to its own stable spot inside a building, opens doors, and is teleported if stuck.

### Scripts (dialogue)
A script is a string, an array, or an object. Array items are strings or line objects:
```json
[
  "A plain line.",
  { "speaker": "Narration", "say": "*Rain taps on the glass.*" },
  { "say": "Pick one?", "points": 10, "choices": [
      { "text": "Option A", "reply": ["Reply line 1", "Reply line 2"], "points": 50, "flag": "chose_a" },
      { "text": "Option B", "reply": "One reply line", "points": -20 }
  ] },
  "This line is shown after whichever reply finishes."
]
```
Placeholders: `{player}`, `{npc}`, `{village}`. Replies can contain their own `choices`.
Effects (on any line or choice): `"points": 40` (friendship, can be negative), `"flag": "name"`, `"give": "minecraft:diamond"` or
`{"item": "...", "count": 3}`, `"command": "say hi"` (runs at op level as the player), `"settle"`, `"marry"`, `"date"`, `"engage"` (all `true`),
or a typed list: `"effects": [{"type": "friendship", "amount": 40}]`.
Mods can add effect types in Java with `HearthvaleApi.registerEffect("mymod:thing", json -> ctx -> ...)`.

### Greetings
Each greeting is a line object plus optional conditions. The most specific matching one is picked (random among ties).

### Events
```json
{ "id": "unique_id", "name": "Display Name", "min_hearts": 4, "trigger": "approach", "requires_event": ["previous_id"], "script": [ ... ] }
```
`name` is what the book lists (defaults to the id, prettified); the book also shows the event's requirements automatically.
`trigger`: `approach` (default - plays when you walk within 5 blocks) or `talk` (only when you right-click). An unseen eligible event always
plays instead of normal chat when you talk. Each event plays once per player.
**Rule of thumb from the bundled cast: 4 events for friends, 6 for romanceable NPCs** (hearts 2, 4, 6, 8, 10 dating, 12 married).

An event may use `"cutscene"` instead of `"script"` - see [CUTSCENE_GUIDE.md](CUTSCENE_GUIDE.md).

### Conditions (greetings and events; all optional)
`min_hearts`, `max_hearts`, `status` (`friend|dating|engaged|married` list), `requires_event` (`"id"` or `"other_ns:other_npc#id"`),
`requires_flag`, `forbids_flag`, `requires_npc` (npc ids that must live in a village), `time` (`morning|afternoon|evening|night|day|"7:00-9:30"`), `weather` (`rain|clear|storm`),
`location` (building type the NPC is currently inside), `season`, `weekday` (`["mon","sat"]`), `birthday` (true/false).

## Skins
Standard 64x64 Minecraft skin PNG at `assets/<ns>/textures/entity/npc/<name>.png`. The dialogue portrait is cropped from the face + hat layer.
