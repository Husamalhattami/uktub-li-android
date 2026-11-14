package com.example.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    fun isPastDate(timestamp: Long): Boolean {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return timestamp < todayStart
    }

    fun isFutureDate(timestamp: Long): Boolean {
        val tomorrowStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return timestamp >= tomorrowStart
    }

    fun localTimestampToUtcPickerMillis(localTimestamp: Long): Long {
        val localCal = Calendar.getInstance().apply {
            timeInMillis = localTimestamp
        }
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, localCal.get(Calendar.YEAR))
            set(Calendar.MONTH, localCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, localCal.get(Calendar.DAY_OF_MONTH))
        }
        return utcCal.timeInMillis
    }

    fun mergeDateWithTimeOfDay(pickedUtcMillis: Long, baseTimeMillis: Long = System.currentTimeMillis()): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = pickedUtcMillis
        }
        val baseCal = Calendar.getInstance().apply {
            timeInMillis = baseTimeMillis
        }
        val resultCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, baseCal.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, baseCal.get(Calendar.MINUTE))
            set(Calendar.SECOND, baseCal.get(Calendar.SECOND))
            set(Calendar.MILLISECOND, baseCal.get(Calendar.MILLISECOND))
        }
        return resultCal.timeInMillis
    }
}
