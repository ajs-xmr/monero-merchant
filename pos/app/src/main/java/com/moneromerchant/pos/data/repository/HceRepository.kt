package com.moneromerchant.pos.data.repository

import com.moneromerchant.pos.data.hce.NfcAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class HceRepository @Inject constructor() {


    private val _uri = MutableStateFlow("")
    val uri: StateFlow<String> = _uri.asStateFlow()

    private val _availability = MutableStateFlow(NfcAvailability.INACTIVE)
    val availability: StateFlow<NfcAvailability> = _availability.asStateFlow()

    fun updateUri(uri: String) {
        _uri.value = uri
    }

    internal fun updateAvailability(availability: NfcAvailability) {
        _availability.value = availability
    }
}