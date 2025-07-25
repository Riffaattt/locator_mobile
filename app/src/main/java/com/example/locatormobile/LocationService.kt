package com.example.locatormobile

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences
    private var firestoreListener: ListenerRegistration? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val client = OkHttpClient()
    private val locationApiUrl = "https://b3431b5205af.ngrok-free.app/api/location/update"

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        startForegroundService()
        observeTriggeredField()
    }

    private fun startForegroundService() {
        val channelId = "LocationServiceChannel"
        val channelName = "Location Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Locator aktif")
            .setContentText("Mendeteksi lokasi secara real-time")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(1, notification)
    }

    private fun observeTriggeredField() {
        val username = sharedPreferences.getString("username", null) ?: return
        val docRef = firestore.collection("locations").document(username)

        firestoreListener = docRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val triggered = snapshot.getBoolean("triggered") ?: false
                if (triggered) {
                    Log.d("LocationService", "Triggered = true")
                    sendLocationOnce()
                }
            }
        }
    }

    private fun sendLocationOnce() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationService", "Izin lokasi tidak diberikan")
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(0)
            .setMaxUpdateAgeMillis(0)
            .setMaxUpdates(1)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location != null) {
                    Log.d("LocationService", "Lokasi didapat: ${location.latitude}, ${location.longitude}")
                    sendLocationToServer(location.latitude, location.longitude)
                } else {
                    Log.e("LocationService", "Lokasi null")
                }

                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun sendLocationToServer(latitude: Double, longitude: Double) {
        val username = sharedPreferences.getString("username", null) ?: return

        val json = JSONObject().apply {
            put("username", username)
            put("latitude", latitude)
            put("longitude", longitude)
        }

        val requestBody = RequestBody.create(
            "application/json".toMediaTypeOrNull(), json.toString()
        )

        val request = Request.Builder()
            .url(locationApiUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LocationService", "Gagal kirim lokasi: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("LocationService", "Lokasi berhasil dikirim")
                    setTriggeredFalse()
                } else {
                    Log.e("LocationService", "Gagal kirim lokasi: ${response.code}")
                }
            }
        })
    }

    private fun setTriggeredFalse() {
        val username = sharedPreferences.getString("username", null) ?: return
        firestore.collection("locations").document(username)
            .update("triggered", false)
            .addOnSuccessListener {
                Log.d("LocationService", "Triggered disetel ke false")
            }
            .addOnFailureListener {
                Log.e("LocationService", "Gagal update triggered: ${it.message}")
            }
    }

    override fun onDestroy() {
        firestoreListener?.remove()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
