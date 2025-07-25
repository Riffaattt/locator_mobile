package com.example.locatormobile

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.*

class HomeActivity : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var latitudeTextView: TextView
    private lateinit var longitudeTextView: TextView
    private lateinit var addressTextView: TextView

    private lateinit var sharedPreferences: SharedPreferences
    private val sharedPrefKey = "user_prefs"
    private val locationApiUrl = "https://b3431b5205af.ngrok-free.app/api/location/update"
    private val firestore = FirebaseFirestore.getInstance()
    private var firestoreListener: ListenerRegistration? = null

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        latitudeTextView = findViewById(R.id.latitudeTextView)
        longitudeTextView = findViewById(R.id.longitudeTextView)
        addressTextView = findViewById(R.id.addressTextView)

        sharedPreferences = getSharedPreferences(sharedPrefKey, MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupLocationCallback()
        checkLocationPermission()
        observeTriggeredField()
    }

    private fun setupLocationCallback() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                if (isLocationFromMockProvider(location)) {
                    showFakeGpsAlert()
                    return
                }

                updateLocationUI(location)
            }
        }
    }

    private fun startRealTimeLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updateLocationUI(location: Location) {
        latitudeTextView.text = "latitude: ${location.latitude}"
        longitudeTextView.text = "longitude: ${location.longitude}"
        getAddressFromLocation(location)
    }

    private fun getAddressFromLocation(location: Location) {
        val geocoder = Geocoder(this, Locale.getDefault())
        Thread {
            try {
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                val address = addresses?.firstOrNull()?.getAddressLine(0) ?: "Alamat tidak ditemukan"

                runOnUiThread {
                    addressTextView.text = "Alamat: $address"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    addressTextView.text = "Alamat: Error (${e.message})"
                }
            }
        }.start()
    }

    private fun observeTriggeredField() {
        val username = sharedPreferences.getString("username", null) ?: return

        val docRef = firestore.collection("locations").document(username)

        firestoreListener = docRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val triggered = snapshot.getBoolean("triggered") ?: false
                if (triggered) {
                    sendLocationOnce()
                }
            }
        }
    }

    private fun sendLocationOnce() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null && !isLocationFromMockProvider(location)) {
                    sendLocationToServer(location.latitude, location.longitude)
                }
            }
    }

    private fun sendLocationToServer(latitude: Double, longitude: Double) {
        val username = sharedPreferences.getString("username", null) ?: return

        val json = JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("username", username)
        }

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, json.toString())

        val request = Request.Builder()
            .url(locationApiUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@HomeActivity, "Gagal kirim lokasi", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@HomeActivity, "Lokasi berhasil dikirim", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@HomeActivity, "Gagal kirim lokasi: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun isLocationFromMockProvider(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            location.isFromMockProvider
        } else {
            false
        }
    }

    private fun showFakeGpsAlert() {
        AlertDialog.Builder(this)
            .setTitle("Peringatan")
            .setMessage("Deteksi penggunaan Fake GPS! Harap matikan aplikasi Fake GPS.")
            .setCancelable(false)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun checkLocationPermission() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            checkBackgroundLocationPermission()
            checkGpsEnabled()
            startRealTimeLocationUpdates()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    AlertDialog.Builder(this)
                        .setTitle("Izin Lokasi Latar Belakang")
                        .setMessage("Aplikasi membutuhkan akses lokasi latar belakang.")
                        .setPositiveButton("Buka Pengaturan") { _, _ ->
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.fromParts("package", packageName, null)
                            startActivity(intent)
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

    private fun checkGpsEnabled() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder(this)
                .setTitle("Aktifkan Lokasi")
                .setMessage("GPS belum aktif. Aktifkan sekarang?")
                .setPositiveButton("Ya") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Tidak", null)
                .show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkBackgroundLocationPermission()
                checkGpsEnabled()
                startRealTimeLocationUpdates()
            } else {
                Toast.makeText(this, "Izin lokasi ditolak!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        firestoreListener?.remove()
    }
}
