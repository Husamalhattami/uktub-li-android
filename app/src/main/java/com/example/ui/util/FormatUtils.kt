package com.example.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ClothingUnits {
    const val PIECE = "حبه"
    const val HALF_DOZEN = "نص درزن"
    const val DOZEN = "درزن"
    const val KORJAH = "كورجه"

    val ALL_UNITS = listOf(PIECE, HALF_DOZEN, DOZEN, KORJAH)

    fun getMultiplier(unit: String): Int {
        return when (unit) {
            HALF_DOZEN -> 6
            DOZEN -> 12
            KORJAH -> 20
            else -> 1
        }
    }

    fun formatUnitDescription(unit: String, count: Double): String {
        val totalPieces = (count * getMultiplier(unit)).toInt()
        return if (unit == PIECE) {
            "$totalPieces حبه"
        } else {
            "${count.toInt()} $unit ($totalPieces حبه)"
        }
    }
}

object ClothingSizes {
    const val BIG = "كبير"
    const val MEDIUM = "وسط"
    const val SMALL = "صغير"
    const val YOUTH = "شبابي"

    val ALL_SIZES = listOf(BIG, MEDIUM, SMALL, YOUTH)
}

object FormatUtils {
    private val decimalFormat = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
    private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())

    fun formatCurrency(amount: Double, currency: String = "ريال"): String {
        return "${decimalFormat.format(amount.toLong())} $currency"
    }

    fun formatNumber(number: Long): String {
        return decimalFormat.format(number)
    }

    fun formatNumber(number: Double): String {
        return decimalFormat.format(number.toLong())
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return "-"
        return dateFormat.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return "-"
        return timeFormat.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        if (timestamp == 0L) return "-"
        return dateTimeFormat.format(Date(timestamp))
    }

    fun isToday(timestamp: Long): Boolean {
        val now = System.currentTimeMillis()
        return formatDate(now) == formatDate(timestamp)
    }

    fun isThisMonth(timestamp: Long): Boolean {
        val monthFormat = SimpleDateFormat("yyyy/MM", Locale.getDefault())
        val now = System.currentTimeMillis()
        return monthFormat.format(Date(now)) == monthFormat.format(Date(timestamp))
    }
}
