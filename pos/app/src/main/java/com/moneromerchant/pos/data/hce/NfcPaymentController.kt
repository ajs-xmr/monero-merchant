package com.moneromerchant.pos.data.hce

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/** Foreground routing and NFC roles for presenting the POS as a payment-request tag. */
internal class NfcPaymentController(
    private val activity: Activity,
    private val onAvailabilityChanged: (NfcAvailability) -> Unit
) {

    private var preferredEmulation: CardEmulation? = null
    private var overriddenAdapter: NfcAdapter? = null
    private var resumed = false
    private var active = false
    private var receiverRegistered = false

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!resumed || intent?.action != NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) return
            val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
            // Toggling NFC invalidates the system's previous foreground configuration.
            release()
            refresh(state)
        }
    }

    fun onResume() {
        resumed = true
        if (!receiverRegistered) {
            try {
                ContextCompat.registerReceiver(
                    activity,
                    adapterStateReceiver,
                    IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
                    ContextCompat.RECEIVER_EXPORTED
                )
                receiverRegistered = true
            } catch (error: RuntimeException) {
                Log.w(LOG_TAG, "Unable to monitor NFC availability", error)
            }
        }
        refresh()
    }

    fun onPause() {
        resumed = false
        active = false
        if (receiverRegistered) {
            receiverRegistered = false
            try {
                activity.unregisterReceiver(adapterStateReceiver)
            } catch (error: RuntimeException) {
                Log.w(LOG_TAG, "Unable to stop monitoring NFC availability", error)
            }
        }
        release()
        onAvailabilityChanged(NfcAvailability.INACTIVE)
    }

    fun setActive(active: Boolean) {
        this.active = active
        refresh()
    }

    private fun refresh(adapterState: Int? = null) {
        if (!resumed || !active) {
            release()
            onAvailabilityChanged(NfcAvailability.INACTIVE)
            return
        }

        try {
            if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
                release()
                onAvailabilityChanged(NfcAvailability.UNSUPPORTED)
                return
            }
            val adapter = activity.getSystemService(NfcManager::class.java)?.defaultAdapter
            if (adapter == null) {
                release()
                onAvailabilityChanged(NfcAvailability.UNSUPPORTED)
                return
            }
            if (!receiverRegistered) {
                release()
                onAvailabilityChanged(NfcAvailability.UNAVAILABLE)
                return
            }
            if (adapterState == NfcAdapter.STATE_TURNING_ON) {
                release()
                onAvailabilityChanged(NfcAvailability.TURNING_ON)
                return
            }
            if (!adapter.isEnabled ||
                adapterState == NfcAdapter.STATE_OFF || adapterState == NfcAdapter.STATE_TURNING_OFF
            ) {
                release()
                onAvailabilityChanged(NfcAvailability.DISABLED)
                return
            }

            if (preferredEmulation == null) {
                val emulation = CardEmulation.getInstance(adapter)
                val service = ComponentName(activity, HceServiceManager::class.java)
                if (emulation.setPreferredService(activity, service)) {
                    preferredEmulation = emulation
                } else {
                    Log.w(LOG_TAG, "Unable to prefer the POS NFC service")
                    release()
                    onAvailabilityChanged(NfcAvailability.UNAVAILABLE)
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && overriddenAdapter == null) {
                // The customer reads the POS tag; the POS must not poll their wallet.
                adapter.setDiscoveryTechnology(
                    activity,
                    NfcAdapter.FLAG_READER_DISABLE,
                    NfcAdapter.FLAG_LISTEN_KEEP
                )
                overriddenAdapter = adapter
            }
            onAvailabilityChanged(NfcAvailability.READY)
        } catch (error: RuntimeException) {
            release()
            onAvailabilityChanged(NfcAvailability.UNAVAILABLE)
            Log.w(LOG_TAG, "Unable to configure NFC payment availability", error)
        }
    }

    private fun release() {
        val emulation = preferredEmulation
        preferredEmulation = null
        if (emulation != null) {
            try {
                emulation.unsetPreferredService(activity)
            } catch (error: RuntimeException) {
                Log.w(LOG_TAG, "Unable to release NFC service preference", error)
            }
        }

        val adapter = overriddenAdapter
        overriddenAdapter = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && adapter != null) {
            try {
                adapter.resetDiscoveryTechnology(activity)
            } catch (error: RuntimeException) {
                Log.w(LOG_TAG, "Unable to restore NFC discovery", error)
            }
        }
    }

    private companion object {
        const val LOG_TAG = "NfcPaymentController"
    }
}
