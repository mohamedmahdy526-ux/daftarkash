package com.example.daftarkash

import android.app.Application
import com.example.daftarkash.data.db.AppDatabase
import com.example.daftarkash.data.repository.DaftarKashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DaftarKashApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { DaftarKashRepository(database) }
}
