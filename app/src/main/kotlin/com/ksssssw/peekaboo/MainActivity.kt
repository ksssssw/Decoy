package com.ksssssw.peekaboo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peekaboo.core.PeekabooProvider
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SampleScreen(
                        onOpenInspector = { openInspector() }
                    )
                }
            }
        }
    }

    private fun openInspector() {
        if (PeekabooProvider.isInitialized() && PeekabooProvider.instance.isRunning()) {
            val port = PeekabooProvider.instance.getPort()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:$port")))
        }
    }
}

@Composable
fun SampleScreen(onOpenInspector: () -> Unit) {
    val repository: PostRepository = koinInject()
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Network Inspector Sample",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    result = runCatching { repository.getPosts() }
                        .fold(
                            onSuccess = { "Fetched ${it.size} posts" },
                            onFailure = { "Error: ${it.message}" }
                        )
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("GET /posts")
        }

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    result = runCatching { repository.getPost(1) }
                        .fold(
                            onSuccess = { "Got post: ${it.title}" },
                            onFailure = { "Error: ${it.message}" }
                        )
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("GET /posts/1")
        }

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    result = runCatching {
                        repository.createPost(Post(title = "Test Post", body = "Test body", userId = 1))
                    }.fold(
                        onSuccess = { "Created: id=${it.id}" },
                        onFailure = { "Error: ${it.message}" }
                    )
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("POST /posts")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenInspector,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Inspector UI")
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (result.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = result,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
