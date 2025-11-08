package com.example.citewise_mobile.utils

import java.util.Calendar
import java.util.TimeZone

/**
 * Utility functions for date comparison.
 */
object DateUtils {

    // Define the application's timezone (e.g., UTC or local default)
    private val TIME_ZONE = TimeZone.getDefault()
    //(will come back)
    /**
     * Checks if the two given epoch timestamps (in milliseconds) fall on the same calendar day.
     *
     * @param timestamp1 The first timestamp.
     * @param timestamp2 The second timestamp (e.g., the selected date from the calendar).
     * @return True if they represent the same day, false otherwise.
     */
    fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        // Create Calendar instances for comparison
        val cal1 = Calendar.getInstance(TIME_ZONE).apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance(TIME_ZONE).apply { timeInMillis = timestamp2 }

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Gets the start of the day (midnight) for a given timestamp.
     */
    fun getStartOfDay(timestamp: Long): Long {
        return Calendar.getInstance(TIME_ZONE).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}