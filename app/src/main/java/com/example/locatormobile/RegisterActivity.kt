package com.example.locatormobile

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

class RegisterActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var registerButton: Button

    private val client = OkHttpClient()
    private val baseUrl = "https://b3431b5205af.ngrok-free.app/api/auth/register"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        emailEditText = findViewById(R.id.emailEditText)
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        registerButton = findViewById(R.id.registerButton)

        registerButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (email.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
                registerUser(email, username, password)
            } else {
                Toast.makeText(this, "Semua field harus diisi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registerUser(email: String, username: String, password: String) {
        val json = JSONObject().apply {
            put("email", email)
            put("username", username)
            put("password", password)
        }

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, json.toString())

        val request = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "Gagal koneksi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        // Simpan ke Firestore
                        val db = FirebaseFirestore.getInstance()
                        val user = hashMapOf(
                            "email" to email,
                            "password" to password // ⚠️ Hanya untuk testing
                        )

                        db.collection("locations")
                            .document(username)
                            .set(user)
                            .addOnSuccessListener {
                                Toast.makeText(this@RegisterActivity, "Registrasi berhasil!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this@RegisterActivity, "Firestore gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this@RegisterActivity, "Registrasi gagal: $responseText", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
