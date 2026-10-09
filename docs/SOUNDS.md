# Sounds and music

How the clock's cue sounds and the tournament music work, and how to add a built-in song or a
sound pack. Everything here is offline and asks for no new permission.

## What there is

| Part | Where | What it does |
|---|---|---|
| Cue sound packs | `core/.../audio/packs/SoundPacks.kt` | A pack has one slot per moment (`CueEvent`): new level, 1 minute left, break starts, break ends, game over. An empty slot is silent; the vibration and the flash still come. **Classic** (the default) is the chime the clock always played, at every change, and no sound with a minute left. |
| Cue player | `core/.../audio/SoundManager.kt` | Plays a cue; dips the music while it sounds (`CueDucking`). `previewSound` plays one even with the sound off (the picker). |
| Which sound when | `tournament-feature/.../clock/ClockCueTimes.kt`, `ClockCues.kt` | Each cue knows its moment; `ClockCues` plays that slot of the picked pack. |
| Music player | `core/.../audio/music/MusicPlayer.kt` | The platform `MediaPlayer` (no library: the APK stays small and plays every format the phone does). Audio focus: a call pauses it until it's over, another music app stops it, a notification dips it. Headphones out pause it. |
| Playlist | `core/.../audio/music/Playlist.kt` | Songs in the host's order, shuffle (every song once a pass; the random numbers come from the caller, so tests seed them), repeat off, all or one. Songs whose files have gone are passed over. |
| Play with the clock | `core/.../audio/music/MusicAutoPlay.kt`, `tournament-feature/.../live/TournamentMusicLink.kt` | Starts the music when the clock runs, pauses it when the clock is paused or over; on breaks it keeps playing, pauses or plays quieter, as the host chose. It acts on changes only, so the host's own Play and Pause stand in between. |
| Screens | `tools-feature/.../composable/MusicScreen.kt`, `CueSoundsScreen.kt` | Tools > Sound > Music (S18) and Cue sounds (S18). |

### Songs from the phone

The host adds songs with the system's file picker (`ACTION_OPEN_DOCUMENT`, audio files only). The
picker lends the app each file it was given, and the app keeps that loan across restarts
(`takePersistableUriPermission`), so no storage or media permission is needed. A removed song's
loan is handed back. A file moved or deleted later shows "File not found" and is skipped.

### In the background

With the screen off and the app still on screen, the music plays on. With the app left for another
one, it plays on only while the clock runs: the live clock's foreground service keeps the app
alive then. Otherwise it pauses, and plays again when the app comes back. Playing music in the
background without the clock would take a media-playback foreground service and a new permission
(`FOREGROUND_SERVICE_MEDIA_PLAYBACK`), which F-Droid lists on the app's page; that's the owner's call.

## Adding a built-in song

There are none yet; Music says so ("None yet") until there are.

1. **Check the licence.** Everything in the APK must be under a free licence, or F-Droid marks the
   app with the NonFreeAssets anti-feature (or won't take the update). Fine: CC0 or public domain,
   CC BY 3.0/4.0, CC BY-SA 3.0/4.0, Free Art License. Not fine: anything "NC" (non-commercial) or
   "ND" (no derivatives), and "royalty-free" or "free for personal use" stock music, which is not
   a free licence. Keep a link to the source and its licence; add the song to the table below.
2. **Make it small.** Ogg (Opus or Vorbis) at 96 kbps or less: about 0.7 MB a minute. Every song
   adds to every download; a few tracks of a few minutes is plenty.
3. **Add the file** as `core/src/main/res/raw/music_<id>.ogg` (lowercase letters, digits and
   underscores only).
4. **Register it** with one line in `BundledTracks.all` (`core/.../audio/music/BundledTracks.kt`):

   ```kotlin
   BundledTrack("night_owl", "Night Owl", R.raw.music_night_owl, "Jane Doe, CC BY 4.0"),
   ```

   The id is saved in playlists: never change it once the song has shipped. The credit shows
   under the title, as CC BY asks.
5. **Tests:** `MusicViewModelTest.noSongsComeWithTheAppYet` says there are none; change it to name
   the new song. The Music goldens don't change (their fixtures set their own list).

## Adding a sound pack or a sound

1. Same licence rules as songs. Keep cue sounds short (under 10 s; the music comes back up after
   15 s whatever the cue does) and small.
2. Add the files as `core/src/main/res/raw/cue_<pack>_<moment>.ogg`.
3. Timing: the sounds for a change start 4 s before it (`AudioConstants.LEVEL_CHANGE_SOUND_LEAD_SECONDS`),
   so they should peak about 4 s in, as the chime does. The 1-minute sound plays on the minute.
4. Add the pack to `SoundPacks.all`, with a name and a one-line description in
   `core/src/main/res/values/strings.xml`, and a sound for each moment it has; leave a moment out
   for no sound. A new sound for one moment of the classic pack is one more line in its map.
5. The id is saved as the host's choice (`sound_pack`): never change it once the pack has shipped.
   A saved id no pack has any more plays the default.
6. Tests: `SoundPacksTest` checks every pack has its own id and a name. The Cue sounds goldens
   (`S18_cue_sounds*`) show every pack, so re-record them (`gh workflow run goldens.yml --ref <branch>`).

## Saved data

New keys only; nothing older was renamed. A backup (Tools > Backup) takes the sound pack and the
music's settings, but not the playlist or where a song was paused: picked songs are this phone's
file loans, so a restore keeps the phone's own list (`MusicPreferences.PHONE_ONLY_KEYS`). Android's
own backup and a move to a new phone leave them out too (PP-137): they live in `phone_prefs`, which
the backup rules exclude. Older versions kept them in `music_prefs`; they move once, keeping their
names (`PhonePrefs.moveOnce`).

| File | Key | What |
|---|---|---|
| `audio_prefs` | `sound_pack` | The picked pack's id (none saved: the default) |
| `phone_prefs` | `playlist` | The playlist as JSON (`format` 1): songs (`ref`, `title`), the current one, shuffle and its order, repeat |
| `music_prefs` | `volume` | The music's own volume, 0 to 1 (the chime's is apart) |
| `music_prefs` | `auto_play` | Play with the clock |
| `music_prefs` | `break_music` | `KEEP`, `PAUSE` or `QUIET` |
| `phone_prefs` | `position_ref`, `position_ms` | Where the current song was paused (saved on a pause, never while playing) |

## Bundled audio

| File | Source | Licence |
|---|---|---|
| `core/src/main/res/raw/blind_level_up.ogg` | The clock's chime, in the app since 1.1.12 | Not recorded yet: fill in from the original |
