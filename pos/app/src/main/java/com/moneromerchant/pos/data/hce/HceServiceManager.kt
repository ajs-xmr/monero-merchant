package com.moneromerchant.pos.data.hce

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.moneromerchant.pos.data.repository.HceRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HceServiceManager : HostApduService() {

    @Inject
    lateinit var hceRepository: HceRepository

    private val tag = NdefTagEmulator()
    private var encodedUri: String? = null

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val uri = hceRepository.uri.value
        if (uri != encodedUri) {
            encodedUri = uri
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
        return tag.processCommand(commandApdu)
    }

    override fun onDeactivated(reason: Int) {
        tag.resetSession()
    }

    private companion object {
        const val LOG_TAG = "HceServiceManager"
    }
}
