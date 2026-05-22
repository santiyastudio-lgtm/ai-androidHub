package com.santiya.localaihub.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TermuxCommandResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        TermuxBridge.onResultIntent(intent)
    }
}
