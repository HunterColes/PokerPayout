package com.huntercoles.pokerpayout.core.audio.music

import com.huntercoles.pokerpayout.core.audio.CueDucking
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The music player, as the cues see it (to dip it) and as the clock sees it (to start and pause it). */
@Module
@InstallIn(SingletonComponent::class)
abstract class MusicModule {
    @Binds
    abstract fun bindCueDucking(player: MusicPlayer): CueDucking

    @Binds
    abstract fun bindMusicControls(player: MusicPlayer): MusicControls

    @Binds
    abstract fun bindMusicLibrary(library: AndroidMusicLibrary): MusicLibrary
}
