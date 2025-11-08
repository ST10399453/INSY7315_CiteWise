package com.example.citewise_mobile.data
data class ConsultantStats(
    val averageTurnaroundDays: Double = 0.0,
    val averageRating: Double = 0.0,
    val monthlyTurnaroundTimes: List<Pair<String, Double>> = emptyList()
)