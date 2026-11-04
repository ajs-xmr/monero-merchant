package com.moneromerchant.pos.data.hce

/** Read-only NFC Forum Type 4 tag with mapping version 2.0 and 256-byte reads. */
internal class NdefTagEmulator {

    private var pendingNdefFile = ByteArray(NDEF_FILE_SIZE)
    private var sessionNdefFile: ByteArray? = null
    private var applicationSelected = false
    private var selectedFile: Int? = null

    /** Replacements take effect on the next connection; clearing aborts any active read. */
    fun updateMessage(message: ByteArray): Boolean {
        val fits = message.size <= NDEF_FILE_SIZE - 2
        val contents = if (fits) message else byteArrayOf()
        pendingNdefFile = ByteArray(NDEF_FILE_SIZE).apply {
            this[0] = (contents.size ushr 8).toByte()
            this[1] = contents.size.toByte()
            contents.copyInto(this, destinationOffset = 2)
        }
        if (contents.isEmpty()) resetSession()
        return fits
    }

    fun resetSession() {
        applicationSelected = false
        selectedFile = null
        sessionNdefFile = null
    }

    fun processCommand(command: ByteArray): ByteArray {
        if (command.size < 4) return status(WRONG_LENGTH)
        if (command[0].unsigned() != 0x00) return status(CLASS_NOT_SUPPORTED)

        return when (command[1].unsigned()) {
            0xA4 -> select(command)
            0xB0 -> readBinary(command)
            0xD6 -> status(COMMAND_NOT_ALLOWED) // Both files are read-only.
            else -> status(INSTRUCTION_NOT_SUPPORTED)
        }
    }

    private fun select(command: ByteArray): ByteArray {
        if (command.size < 5) return status(WRONG_LENGTH)
        val dataLength = command[4].unsigned()
        // SELECT accepts a short Lc followed by data, with or without a trailing Le.
        if (dataLength == 0 ||
            (command.size != 5 + dataLength && command.size != 6 + dataLength)
        ) {
            return status(WRONG_LENGTH)
        }
        val p1 = command[2].unsigned()
        val p2 = command[3].unsigned()
        if (p2 != 0x00 && p2 != 0x0C) return status(WRONG_PARAMETERS)
        val data = command.copyOfRange(5, 5 + dataLength)

        return when (p1) {
            0x04 -> {
                if (!data.contentEquals(NDEF_APPLICATION_ID)) return status(FILE_NOT_FOUND)
                applicationSelected = true
                selectedFile = null
                // Never mix NLEN from one payment with bytes from a newer payment.
                if (sessionNdefFile == null) sessionNdefFile = pendingNdefFile
                status(SUCCESS)
            }
            0x00, 0x02 -> {
                if (!applicationSelected) return status(CONDITIONS_NOT_SATISFIED)
                if (dataLength != 2) return status(WRONG_LENGTH)
                val fileId = (data[0].unsigned() shl 8) or data[1].unsigned()
                if (fileId != CC_FILE_ID && fileId != NDEF_FILE_ID) {
                    return status(FILE_NOT_FOUND)
                }
                selectedFile = fileId
                status(SUCCESS)
            }
            else -> status(WRONG_PARAMETERS)
        }
    }

    private fun readBinary(command: ByteArray): ByteArray {
        val length = when {
            command.size == 5 -> command[4].unsigned().let { if (it == 0) 256 else it }
            command.size == 7 && command[4].unsigned() == 0 -> {
                val extendedLength = (command[5].unsigned() shl 8) or command[6].unsigned()
                if (extendedLength == 0) 65536 else extendedLength
            }
            else -> return status(WRONG_LENGTH)
        }
        // Only offsets in the currently selected EF are supported, not short EF IDs.
        if (command[2].unsigned() and 0x80 != 0) return status(WRONG_PARAMETERS)
        if (length > MAX_READ_SIZE) return status(0x6C00) // Retry with Le = 00 (256).

        val file = when (selectedFile) {
            CC_FILE_ID -> CAPABILITY_CONTAINER
            NDEF_FILE_ID -> sessionNdefFile
            else -> null
        } ?: return status(COMMAND_NOT_ALLOWED)
        val offset = (command[2].unsigned() shl 8) or command[3].unsigned()
        if (offset >= file.size) return status(WRONG_OFFSET)

        val end = minOf(offset + length, file.size)
        val result = if (end - offset == length) SUCCESS else END_OF_FILE
        return file.copyOfRange(offset, end) + status(result)
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private fun status(value: Int): ByteArray {
        return byteArrayOf((value ushr 8).toByte(), value.toByte())
    }

    private companion object {
        const val CC_FILE_ID = 0xE103
        const val NDEF_FILE_ID = 0xE104
        const val NDEF_FILE_SIZE = 4096 // Includes the two-byte NLEN field.
        const val MAX_READ_SIZE = 256

        const val SUCCESS = 0x9000
        const val END_OF_FILE = 0x6282
        const val WRONG_LENGTH = 0x6700
        const val CONDITIONS_NOT_SATISFIED = 0x6985
        const val COMMAND_NOT_ALLOWED = 0x6986
        const val FILE_NOT_FOUND = 0x6A82
        const val WRONG_PARAMETERS = 0x6A86
        const val WRONG_OFFSET = 0x6B00
        const val INSTRUCTION_NOT_SUPPORTED = 0x6D00
        const val CLASS_NOT_SUPPORTED = 0x6E00

        val NDEF_APPLICATION_ID = byteArrayOf(
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01
        )

        val CAPABILITY_CONTAINER = byteArrayOf(
            0x00, 0x0F, // CCLEN: 15 bytes, excluding response status words.
            0x20, // NDEF mapping version 2.0.
            (MAX_READ_SIZE ushr 8).toByte(), MAX_READ_SIZE.toByte(),
            0x00, 0xFF.toByte(), // Maximum command data length.
            0x04, 0x06, // NDEF File Control TLV.
            (NDEF_FILE_ID ushr 8).toByte(), NDEF_FILE_ID.toByte(),
            (NDEF_FILE_SIZE ushr 8).toByte(), NDEF_FILE_SIZE.toByte(),
            0x00, 0xFF.toByte() // Read access granted; write access denied.
        )
    }
}
