package com.example.locatormobile

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.widget.Toast
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
    private lateinit var locationCallback: LocationCallback
    private lateinit var firestore: FirebaseFirestore
    private var listener: ListenerRegistration? = null
    private val client = OkHttpClient()

    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        firestore = FirebaseFirestore.getInstance()

        setupLocationUpdates()

        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", null)

        if (username != null) {
            val docRef = firestore.collection("locations").document(username)
            listener = docRef.addSnapshotListener { snapshot, _ ->
                val triggered = snapshot?.getBoolean("triggered") ?: false
                if (triggered) {
                    sendLocationNow(username)
                }
            }
        }
    }

    private fun setupLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null && !isLocationFromMockProvider(location)) {
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }

    private fun sendLocationNow(username: String) {
        if (currentLatitude != null && currentLongitude != null) {
            val location = Location("").apply {
                latitude = currentLatitude!!
                longitude = currentLongitude!!
            }
            sendLocation(username, location)
        } else {
            Toast.makeText(this, "Lokasi belum tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendLocation(username: String, location: Location) {
        val json = JSONObject().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("username", username)
        }

        val body = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            json.toString()
        )

        val request = Request.Builder()
            .url("https://5c81c08c8a87.ngrok-free.app/api/location/update")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Log optional
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    firestore.collection("locations")
                        .document(username)
                        .update("triggered", false)
                }
            }
        })
    }

    private fun isLocationFromMockProvider(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            location.isFromMockProvider
        } else false
    }

    private fun startForegroundService() {
        val channelId = "location_channel"
        val channelName = "Location Tracking"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Melacak Lokasi")
            .setContentText("Service berjalan di latar belakang")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
