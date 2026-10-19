package io.github.kobiregev.volt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Empty on purpose: Android reads only the manifest meta-data of this receiver and never runs it. */
class RemoteKeymapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
