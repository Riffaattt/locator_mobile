package com.example.locatormobile

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerNavButton: Button

    private val client = OkHttpClient()
    private val loginUrl = "https://eec44f337f52.ngrok-free.app/api/auth/login"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerNavButton = findViewById(R.id.registerNavButton)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                loginUser(username, password)
            } else {
                Toast.makeText(this, "Isi username dan password!", Toast.LENGTH_SHORT).show()
            }
        }

        registerNavButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser(username: String, password: String) {
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
        }

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, json.toString())

        val request = Request.Builder()
            .url(loginUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Gagal koneksi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        val db = FirebaseFirestore.getInstance()
                        db.collection("locations").document(username).get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
                                    sharedPreferences.edit().putString("username", username).apply()
                                    val loginLog = hashMapOf(
                                        "timestamp" to System.currentTimeMillis()
                                    )
                                    db.collection("login_logs").add(loginLog)

                                    Toast.makeText(this@MainActivity, "Login berhasil!", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@MainActivity, HomeActivity::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(this@MainActivity, "User tidak ditemukan di Firestore /locations", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(this@MainActivity, "Gagal akses Firestore", Toast.LENGTH_SHORT).show()
                            }

                    } else {
                        Toast.makeText(this@MainActivity, "Login gagal: $responseBody", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}
