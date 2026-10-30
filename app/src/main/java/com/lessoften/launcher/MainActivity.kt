package com.lessoften.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var bucket by mutableStateOf(Bucket.Primary)
    private var revision by mutableIntStateOf(0)
    private var setupShown = false
    private val catalogChanged: () -> Unit = { revision++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state.catalog.listen(catalogChanged)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF101010), onBackground = Color(0xFFE0E0E0))) {
                BackHandler(bucket != Bucket.Primary) { bucket = Bucket.Primary }
                revision
                val apps = state.catalog.list(bucket)
                LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 28.dp, vertical = 22.dp)) {
                    if (bucket != Bucket.Primary) item {
                        Text("Home", Modifier.clickable { bucket = Bucket.Primary }.padding(vertical = 16.dp), color = Color.Gray, fontSize = 16.sp)
                        Text(bucket.name, Modifier.padding(bottom = 24.dp), color = Color.LightGray, fontSize = 18.sp)
                    }
                    items(apps, key = { it.component.flattenToString() }) { app ->
                        Text(app.label, Modifier.fillMaxWidth().clickable { launch(app) }.padding(vertical = 15.dp),
                            color = MaterialTheme.colorScheme.onBackground, fontSize = if (bucket == Bucket.Primary) 28.sp else 23.sp)
                    }
                    if (bucket == Bucket.Primary) {
                        item { Spacer(Modifier.height(30.dp)) }
                        item { NavigationButton("Secondary") { bucket = Bucket.Secondary } }
                        item { NavigationButton("Restricted") { bucket = Bucket.Restricted } }
                    }
                }
            }
        }
    }
    @Composable private fun NavigationButton(label: String, action: () -> Unit) {
        Text(label, Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 17.dp), color = Color.Gray, fontSize = 18.sp)
    }
    private fun launch(entry: AppEntry) {
        if (entry.bucket == Bucket.Restricted) {
            val service = GateService.current
            if (service == null) startActivity(Intent(this, SetupActivity::class.java)) else service.requestLaunch(entry)
        } else try { state.catalog.launch(entry) } catch (e: Exception) { state.failure = e.message; startActivity(Intent(this, SetupActivity::class.java)) }
    }
    override fun onResume() {
        super.onResume(); state.catalog.refresh()
        if (GateService.current == null) state.repairDisplay()
        if (!setupShown && state.capabilityProblem() != null) {
            setupShown = true; startActivity(Intent(this, SetupActivity::class.java))
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); bucket = Bucket.Primary
        GateService.current?.onHomeRequested()
    }
    override fun onDestroy() { state.catalog.unlisten(catalogChanged); super.onDestroy() }
}
