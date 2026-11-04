package com.moneromerchant.pos.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class HceRepository @Inject constructor() {
class HceRepository @Inject constructor(
    private val context: Context
) {

    fun updateUri(uri: String) {
        _uri.value = uri
    }
}