# Sounds and music

How the clock's cue sounds and the tournament music work, and how to add a built-in song or a
sound pack. Everything here is offline and asks for no new permission.

## What there is

| Part | Where | What it does |
|---|---|---|
| Cue sound packs | `core/.../audio/packs/SoundPacks.kt` | A pack has one slot per moment (`CueEvent`): new level, 1 minute left, break starts, break ends, game over, and the night's big moments (PP-111): one slot for the bubble, in the money, the final table and heads-up, one for the champion. An empty slot is silent; the vibration and the flash still come. **Classic** (the default) is the cue sound the clock always played, at every change, and no sound with a minute left; the champion gets it too, the other big moments none. **Chimes** (`chimes`, #7) has a bell for every moment, a soft one with a minute left; its change sounds are padded with silence so the strike lands 4 s in. |
| Cue player | `core/.../audio/SoundManager.kt` | Plays a cue; dips the music while it sounds (`CueDucking`). `previewSound` plays one even with the cue sounds off (the previews on Cue sounds). |
| Which sound when | `tournament-feature/.../clock/ClockCueTimes.kt`, `ClockCues.kt` | Each cue knows its moment; `ClockCues` plays that slot of the picked pack. A big moment's sound plays as the knockout that brings it is recorded (`ClockCues.playMoment`, from the clock's `TimerViewModel`), muted or not as the cue sounds' switch says. |
| Music player | `core/.../audio/music/MusicPlayer.kt` | The platform `MediaPlayer` (no library: the APK stays small and plays every format the phone does). Audio focus: a call pauses it until it's over, another music app stops it, a notification dips it. Headphones out pause it. |
| Playlist | `core/.../audio/music/Playlist.kt` | Songs in the host's order, shuffle (every song once a pass; the random numbers come from the caller, so tests seed them), repeat off, all or one. Songs whose files have gone are passed over. |
| Play with the clock | `core/.../audio/music/MusicAutoPlay.kt`, `tournament-feature/.../live/TournamentMusicLink.kt` | Starts the music when the clock runs, pauses it when the clock is paused or over; on breaks it keeps playing, pauses or plays quieter, as the host chose. It acts on changes only, so the host's own Play and Pause stand in between. |
| Screens | `tools-feature/.../composable/CueSoundsScreen.kt`, `MusicScreen.kt` | Cue sounds (S18) and Music (S18), from Tools > Sound and music. Cue sounds is the one place for the clock's sounds (#79). |

### Where the host sets the sound

One setting turns the cue sounds on or off: `is_muted` in `audio_prefs` (the key the old volume
dialog wrote; never rename it). It has one name everywhere, "cue sounds":

- **Cue sounds** (Tools > Sound and music > Cue sounds, S18; `CueSoundsScreen.kt`,
  `CueSoundsViewModel.kt`): the switch, the volume (`volume`, kept while they are off), and the
  packs, each moment's sound with a play button. A preview plays even with the cue sounds off:
  hearing it is the point, and the screen says so. There is no separate test button (the old
  "Test chime" on the Tools list was the classic pack's "New level" preview).
- **The clock's bell** (tap: "Mute cue sounds" / "Unmute cue sounds"; hold: Cue sounds) and its
  ⋮ > **Sound settings**, which opens Cue sounds on top of the clock with Tools selected, so Back
  comes home (`TournamentNavigationFactory`; the route is still `NavigationDestination.CueSounds`,
  since route names are saved navigation state).
- **The Bank's bell**: the same mute, with the same words.
- **Tools > Sound and music**: a Cue sounds row ("On · Classic"), the quiet cues (Vibrate, Flash,
  each independent of the cue sounds) and the Music row.

The cue sounds are not the music: muting them leaves the music playing, and the music has its own
volume on the Music screen.

### Your own sound for the changes

Cue sounds has a **Your own sound** card above the packs (#22; `OwnSoundCard.kt`,
`CueSoundsViewModel.kt`). Turning it on opens the system's file picker (`OpenDocument`, `audio/*`;
no storage permission); `CueSoundFiles.keep` takes the persistable read grant and measures the file.
A file it can't read is refused in one line and nothing changes; a cancelled picker changes nothing.
With a file on, the host sets:

- **Peak** (0 to the length, 0.1 s steps): where the change lands. The clock starts the file this
  long before each change (the lead, `ClockCueTimes`), so the peak sits on the change.
- **Play to the end**, or an **Ends at** point (peak to the length), where it stops with a short fade.
- **Preview**: plays the file from 0 to its end (`SoundManager.previewFile`, even with the cue
  sounds off) and fills a bar the length of what plays, with a "Change" mark at the peak, so the
  host sees the peak land on the mark.

The file plays only the four change sounds (New level, Break starts, Break ends, Game over). The
picked pack stays the fallback and keeps 1 minute left, the big moments and the champion. A newly
picked file keeps the old peak, clamped to its length. **Choose another file** swaps it (the old
grant is released); **Stop using it** releases the grant and clears the file. When the file can't
be read (moved, deleted, its grant gone: found by the clock or on opening the screen), the pack
plays and the card says "Your sound file can't be found; playing <pack> instead." until a new pick.

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
   so they should peak about 4 s in, as the classic cue sound does. The 1-minute sound plays on the minute, and
   a big moment's at once, so it should start strong and stay short (2 to 4 s).
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

The own cue sound (`cue_custom_*`, `CustomCueSoundPreferences`) is in `phone_prefs` for the same
reason: the file is a loan from this phone's file picker, and its grant means nothing on another
phone, so no backup (the app's or Android's) carries it. A restored phone plays the restored pack.

| File | Key | What |
|---|---|---|
| `audio_prefs` | `sound_pack` | The picked pack's id (none saved: the default) |
| `phone_prefs` | `playlist` | The playlist as JSON (`format` 1): songs (`ref`, `title`), the current one, shuffle and its order, repeat |
| `music_prefs` | `volume` | The music's own volume, 0 to 1 (the cue sounds' is apart) |
| `music_prefs` | `auto_play` | Play with the clock |
| `music_prefs` | `break_music` | `KEEP`, `PAUSE` or `QUIET` |
| `phone_prefs` | `position_ref`, `position_ms` | Where the current song was paused (saved on a pause, never while playing) |
| `phone_prefs` | `cue_custom_on` | The own sound plays the four change cues (false: the pack's) |
| `phone_prefs` | `cue_custom_uri` | The picked file's content URI, as the file picker lent it |
| `phone_prefs` | `cue_custom_length_ms` | The file's length, measured when picked |
| `phone_prefs` | `cue_custom_peak_ms` | Where the change lands, 0 to the length |
| `phone_prefs` | `cue_custom_end_ms` | Where it stops with a short fade, peak to the length; 0 plays to the end |
| `phone_prefs` | `cue_custom_missing` | The file could not be read at the last try (the pack played) |

## Bundled audio

| File | Source | Licence |
|---|---|---|
| `core/src/main/res/raw/blind_level_up.ogg` | The clock's cue sound (the classic pack's), in the app since 1.1.12 | Not recorded yet: fill in from the original |
| `core/src/main/res/raw/cue_chimes_level_up.ogg` | Chimes, new level: ElevenLabs sound effects, generated by Hunter 2026-10-31; padded to peak at 4.00 s | ElevenLabs output, Hunter's; free licence not recorded yet |
| `core/src/main/res/raw/cue_chimes_one_minute.ogg` | Chimes, 1 minute left: ElevenLabs sound effects, generated by Hunter 2026-10-31 | ElevenLabs output, Hunter's; free licence not recorded yet |
| `core/src/main/res/raw/cue_chimes_break_start.ogg` | Chimes, break starts: ElevenLabs sound effects, generated by Hunter 2026-10-31; padded to peak at 4.00 s | ElevenLabs output, Hunter's; free licence not recorded yet |
| `core/src/main/res/raw/cue_chimes_break_end.ogg` | Chimes, break ends: ElevenLabs sound effects, generated by Hunter 2026-10-31; padded to peak at 4.00 s | ElevenLabs output, Hunter's; free licence not recorded yet |
| `core/src/main/res/raw/cue_chimes_game_over.ogg` | Chimes, game over: ElevenLabs sound effects, generated by Hunter 2026-10-31; padded to peak at 4.00 s | ElevenLabs output, Hunter's; free licence not recorded yet |
| `core/src/main/res/raw/cue_chimes_big_moment.ogg` | Chimes, big moments: ElevenLabs sound effects, generated by Hunter 2026-10-31 | ElevenLabs output, Hunter's; free licence not recorded yet |
| `core/src/main/res/raw/cue_chimes_champion.ogg` | Chimes, champion: ElevenLabs sound effects, generated by Hunter 2026-10-31 | ElevenLabs output, Hunter's; free licence not recorded yet |
