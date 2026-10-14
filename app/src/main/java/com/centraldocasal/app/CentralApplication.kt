package com.centraldocasal.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.json.JSONObject

open class CentralApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val config = JSONObject(assets.open("firebase-config.json").bufferedReader().use { it.readText() })
        val client = config.getJSONArray("client").getJSONObject(0)
        if (FirebaseApp.getApps(this).isEmpty()) FirebaseApp.initializeApp(this, FirebaseOptions.Builder()
            .setApplicationId(client.getJSONObject("client_info").getString("mobilesdk_app_id"))
            .setApiKey(client.getJSONArray("api_key").getJSONObject(0).getString("current_key"))
            .setProjectId(config.getJSONObject("project_info").getString("project_id"))
            .setStorageBucket(config.getJSONObject("project_info").getString("storage_bucket"))
            .setGcmSenderId(config.getJSONObject("project_info").getString("project_number"))
            .build())
    }
}
