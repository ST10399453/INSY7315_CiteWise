// app/src/main/java/com/example/citewise_mobile/core/WorkerConstraints.kt
package com.example.citewise_mobile.utils

import androidx.work.Constraints
import androidx.work.NetworkType

object WorkerConstraints {
    val connected: Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
