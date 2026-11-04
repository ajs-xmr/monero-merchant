package com.moneromerchant.pos.data.hce

import android.app.Service
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class HceServiceManager : HostApduService() {

    private val tag = NdefTagEmulator()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("uri")?.let { uri ->
            try {
                val message = if (uri.isEmpty()) {
                    byteArrayOf()
                } else {
                    NdefMessage(NdefRecord.createUri(uri)).toByteArray()
                }
                if (!tag.updateMessage(message)) {
                    Log.w(LOG_TAG, "NFC payment request exceeds the NDEF file capacity")
                }
            } catch (_: IllegalArgumentException) {
                tag.updateMessage(byteArrayOf())
                Log.w(LOG_TAG, "Unable to encode NFC payment request")
            }
        }
        return Service.START_NOT_STICKY
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        return tag.processCommand(commandApdu)
    }

    override fun onDeactivated(reason: Int) {
        tag.resetSession()
    }

    private companion object {
        const val LOG_TAG = "HceServiceManager"
    }
}
