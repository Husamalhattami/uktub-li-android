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
    private val decimalFormatTl = ThreadLocal.withInitial {
        DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
    }
    private val plainInputFormatTl = ThreadLocal.withInitial {
        DecimalFormat("0.##", DecimalFormatSymbols(Locale.US))
    }
    private val dateFormatTl = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    }
    private val timeFormatTl = ThreadLocal.withInitial {
        SimpleDateFormat("hh:mm a", Locale.getDefault())
    }
    private val dateTimeFormatTl = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault())
    }

    fun formatCurrency(amount: Double, currency: String = "ريال"): String {
        val clean = if (kotlin.math.abs(amount) < 0.005) 0.0 else amount
        return "${decimalFormatTl.get()!!.format(clean)} $currency"
    }

    fun formatNumber(number: Long): String {
        return decimalFormatTl.get()!!.format(number)
    }

    fun formatNumber(number: Double): String {
        val clean = if (kotlin.math.abs(number) < 0.005) 0.0 else number
        return decimalFormatTl.get()!!.format(clean)
    }

    fun formatInputNumber(number: Double): String {
        val clean = if (kotlin.math.abs(number) < 0.005) 0.0 else number
        return plainInputFormatTl.get()!!.format(clean)
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return "-"
        return dateFormatTl.get()!!.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return "-"
        return timeFormatTl.get()!!.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        if (timestamp == 0L) return "-"
        return dateTimeFormatTl.get()!!.format(Date(timestamp))
    }

    fun todayStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun tomorrowStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    fun monthStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun isToday(timestamp: Long): Boolean {
        val start = todayStartMillis()
        return timestamp in start until (start + 86_400_000L)
    }

    fun isThisMonth(timestamp: Long): Boolean {
        val start = monthStartMillis()
        val end = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
        }.timeInMillis
        return timestamp in start until end
    }

    fun isPastDate(timestamp: Long): Boolean = timestamp < todayStartMillis()

    fun isFutureDate(timestamp: Long): Boolean = timestamp >= tomorrowStartMillis()

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
