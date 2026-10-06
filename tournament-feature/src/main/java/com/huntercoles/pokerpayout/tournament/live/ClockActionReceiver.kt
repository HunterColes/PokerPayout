package com.huntercoles.pokerpayout.tournament.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCommand
import com.huntercoles.pokerpayout.tournament.domain.clock.ClockCommands
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The live clock notification's Pause and Resume (PP-081). Not exported: only the notification's
 * own PendingIntents reach it. The command changes the saved clock ([ClockCommands]); the clock's
 * screen, if alive, and the notification's service follow it from there.
 */
class ClockActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun clockCommands(): ClockCommands
    }

    override fun onReceive(context: Context, intent: Intent) {
        val command = commandOf(intent.action) ?: return
        EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
            .clockCommands()
            .perform(command)
    }

    companion object {
        const val ACTION_PAUSE = "com.huntercoles.pokerpayout.action.PAUSE_CLOCK"
        const val ACTION_RESUME = "com.huntercoles.pokerpayout.action.RESUME_CLOCK"

        /** The broadcast a notification button sends for [command]. */
        fun intent(context: Context, command: ClockCommand): Intent =
            Intent(context, ClockActionReceiver::class.java).setAction(
                when (command) {
                    ClockCommand.PAUSE -> ACTION_PAUSE
                    ClockCommand.RESUME -> ACTION_RESUME
                },
            )

        /** The command a broadcast carries, or null for anything else. */
        fun commandOf(action: String?): ClockCommand? = when (action) {
            ACTION_PAUSE -> ClockCommand.PAUSE
            ACTION_RESUME -> ClockCommand.RESUME
            else -> null
        }
    }
}
