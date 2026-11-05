# Real Time Lyrics

Shows lyrics from your Spotify or browser music floating around you in Minecraft, in sync with the song.

Fabric mod for Minecraft 1.21.11. Each new line appears in your line of sight, words pop in one by one
as they're sung with a glowing colored outline and sparkles, then the line drifts apart when the next one starts.
A title card shows when a new song starts, and ♪ ♫ ♪ notes float during instrumental parts.

## How it works
- **What's playing:** Windows media controls (same info as the volume flyout). Linux uses `playerctl`, macOS asks Spotify/Music.
- **Lyrics:** time-synced lyrics from [lrclib.net](https://lrclib.net) (free, no account).
- Messy YouTube titles like `Artist - Song (Official Video)` from `ArtistVEVO` are cleaned up before searching.

## Keys (change in Options → Controls → Real Time Lyrics)
| Key | Action |
|-----|--------|
| `K` | Show / hide lyrics |
| `]` | Lyrics sooner (+200 ms) |
| `[` | Lyrics later (-200 ms) |
| `\` | "Song just started" — resets timing (for players that don't report position) |

## Settings
Press **Esc** in game and click **♫ Lyrics** (top-left) to open the settings. Changes show up live:
lyrics on/off, follow me (lines move with you, or stay where they appeared), floating motion, glow outline,
particles, song title card, see through walls, status messages, colors (rainbow, white, gold, aqua, pink, purple, green),
text size, distance, height, spread, and a timing offset (e.g. for Bluetooth headphone delay).
They're saved in `config/realtimelyrics.json`. You can also bind a key to open the settings in Controls.

## Building
Gradle needs Java 25 to run (Loom requirement); the mod itself compiles for Java 21.
```
./gradlew build
```
The jar ends up in `build/libs/`. Needs Fabric Loader and Fabric API.

## License
MIT
