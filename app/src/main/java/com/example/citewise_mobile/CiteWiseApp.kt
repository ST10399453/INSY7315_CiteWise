package com.example.citewise_mobile

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.citewise_mobile.offline.MessagesSyncWorker
import com.example.citewise_mobile.offline.RequestsPullWorker
import com.example.citewise_mobile.offline.RequestsSyncWorker
import com.example.citewise_mobile.offline.DocumentsSyncWorker
import com.example.citewise_mobile.offline.UsersSyncWorker

class CiteWiseApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Start periodic background syncs
        UsersSyncWorker.schedule(this)            // offline users directory
        MessagesSyncWorker.schedule(this)         // chat in/out sync with Firestore
        RequestsSyncWorker.schedulePeriodic(this) // service-requests -> your API
        RequestsPullWorker.schedule(this)
        DocumentsSyncWorker.schedule(this)
    }
}
