package com.gopi.zebrabarcodescanner.scanningModule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

class DWReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.v("tag", "BroadcastReceiver-onReceive()")
        //  This method is called when the BroadcastReceiver is receiving an Intent broadcast.
        //  Notify registered observers
        val action = intent.action

        val command = intent.getStringExtra("COMMAND")
        val commandidentifier = intent.getStringExtra("COMMAND_IDENTIFIER")
        val result = intent.getStringExtra("RESULT")

        var bundle = Bundle()
        var resultInfo = ""
        if (intent.hasExtra("RESULT_INFO")) {
            bundle = intent.getBundleExtra("RESULT_INFO")
            val keys = bundle.keySet()
            for (key in keys) {
                resultInfo += """
                    $key: ${bundle.getString(key)}
                    
                    """.trimIndent()
            }
        }

        val text = """
            Command: $command
            Result: $result
            Result Info: $resultInfo
            CID:$commandidentifier
            """.trimIndent()

        Log.v("tag", "onReceive: $text")

        ObservableObject.instance.updateValue(intent)
    }
}
