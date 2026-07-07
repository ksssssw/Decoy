package com.ksssssw.decoy

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
import com.decoy.core.DecoyLauncher
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
                    SampleScreen(onOpenInspector = { openInspector() })
                }
            }
        }
    }

    private fun openInspector() {
        val url = DecoyLauncher.getInspectorUrl() ?: return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
fun SampleScreen(onOpenInspector: () -> Unit) {
    val ktorRepo: PostRepository = koinInject()
    val retrofitApi: RetrofitPostApi = koinInject()
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun run(label: String, block: suspend () -> String) {
        scope.launch {
            isLoading = true
            result = runCatching { block() }
                .fold(
                    onSuccess = { "$label → $it" },
                    onFailure = { "$label → Error: ${it.message}" }
                )
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Decoy Sample",
            style = MaterialTheme.typography.headlineSmall
        )

        InspectorCard(onOpenInspector)

        SectionLabel("Retrofit (OkHttp Interceptor)")
        ScenarioButton("GET /posts", isLoading) {
            run("Retrofit GET") { "${retrofitApi.getPosts().size} posts" }
        }
        ScenarioButton("POST /posts (request body)", isLoading) {
            run("Retrofit POST") {
                "created id=${retrofitApi.createPost(Post(title = "Hello", body = "from Retrofit", userId = 1)).id}"
            }
        }
        ScenarioButton("GET 404", isLoading) {
            run("Retrofit 404") { retrofitApi.getPost(999_999_999).toString() }
        }
        ScenarioButton("GET delay 3s (httpbingo)", isLoading) {
            run("Retrofit delay") { "${retrofitApi.getRaw("https://httpbingo.org/delay/3").size} fields" }
        }

        SectionLabel("Ktor Client (Plugin)")
        ScenarioButton("GET /posts", isLoading) {
            run("Ktor GET") { "${ktorRepo.getPosts().size} posts" }
        }
        ScenarioButton("POST /posts (request body)", isLoading) {
            run("Ktor POST") {
                "created id=${ktorRepo.createPost(Post(title = "Hello", body = "from Ktor", userId = 1)).id}"
            }
        }
        ScenarioButton("GET 404", isLoading) {
            run("Ktor 404") { "status ${ktorRepo.notFound()}" }
        }
        ScenarioButton("GET delay 3s (httpbingo)", isLoading) {
            run("Ktor delay") { "status ${ktorRepo.delayed(3)}" }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (result.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
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

@Composable
private fun InspectorCard(onOpenInspector: () -> Unit) {
    val url = DecoyLauncher.getInspectorUrl()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (url != null) {
                Text("Inspector: $url", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "View on PC: adb forward tcp:8090 tcp:8090",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onOpenInspector, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Inspector in Browser")
                }
            } else {
                Text(
                    "Decoy disabled (release build — no-op)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ScenarioButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading
    ) {
        Text(text)
    }
}
