package com.ksssssw.peekaboo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.peekaboo.core.PeekabooProvider
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {

    private val apiService: SampleApiService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnGetPosts).setOnClickListener {
            lifecycleScope.launch {
                runCatching { apiService.getPosts() }
                    .onSuccess { showToast("Fetched ${it.size} posts") }
                    .onFailure { showToast("Error: ${it.message}") }
            }
        }

        findViewById<Button>(R.id.btnGetPost).setOnClickListener {
            lifecycleScope.launch {
                runCatching { apiService.getPost(1) }
                    .onSuccess { showToast("Got post: ${it.title}") }
                    .onFailure { showToast("Error: ${it.message}") }
            }
        }

        findViewById<Button>(R.id.btnCreatePost).setOnClickListener {
            lifecycleScope.launch {
                runCatching {
                    apiService.createPost(Post(null, "Test Post", "Test body", 1))
                }
                    .onSuccess { showToast("Created: id=${it.id}") }
                    .onFailure { showToast("Error: ${it.message}") }
            }
        }

        findViewById<Button>(R.id.btnOpenInspector).setOnClickListener {
            if (PeekabooProvider.isInitialized()) {
                val port = PeekabooProvider.instance.getPort()
                val uri = Uri.parse("http://localhost:$port")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                showToast("Run: adb forward tcp:$port tcp:$port")
            } else {
                showToast("Inspector not initialized (release build?)")
            }
        }
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
