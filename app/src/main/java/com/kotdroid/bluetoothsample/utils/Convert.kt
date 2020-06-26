package com.kotdroid.bluetoothsample.utils

import java.util.*

fun convertFromInteger(i: Int): UUID {
    val MSB = 0x0000000000001000L
    val LSB = -0x7fffff7fa064cb05L
    val value = (i and -0x1).toLong()
    return UUID(MSB or (value shl 32), LSB)
}