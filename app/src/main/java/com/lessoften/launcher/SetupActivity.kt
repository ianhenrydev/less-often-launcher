package com.lessoften.launcher

import android.app.role.RoleManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class SetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var tick by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
            tick
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF171917), surface = Color(0xFF171917), primary = Color(0xFFA6B79A), onBackground = Color(0xFFE6E8DF), onSurface = Color(0xFFE6E8DF))) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Less Often setup", style = MaterialTheme.typography.headlineMedium)
                        Text(state.capabilityProblem() ?: "Accessibility connected and secure-setting grant present. Device acceptance checks are still required.")
                        if (GateService.current == null) Text("If Less Often already appears enabled in Accessibility settings, turn it off and back on to reconnect the service.")
                        Text("Gates use package/window metadata. No message text is collected. Notifications and inline actions stay native. Access lasts 600 elapsed seconds per app; disabling this service bypasses the gate.")
                        val usageGranted = getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
                        val notificationsGranted = getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(this@SetupActivity, UsageNotificationListener::class.java))
                        Text("Usage access: ${if (usageGranted) "enabled" else "not enabled"}. Used for local device and Restricted app activity.")
                        Button(onClick = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Usage access settings") }
                        Text("Notification access: ${if (notificationsGranted) "enabled" else "not enabled"}. Counts eligible notifications without storing their contents.")
                        Button(onClick = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Notification access settings") }
                        Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility settings") }
                        Text("For a sideloaded app, Android may first require App info → menu → Allow restricted settings. Enable this yourself, then enable the Less Often service above.")
                        Text("On the development computer run:\nadb shell pm grant com.lessoften.launcher android.permission.WRITE_SECURE_SETTINGS")
                        Button(onClick = {
                            val roles = getSystemService(RoleManager::class.java)
                            startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                        }) { Text("Choose Home app") }
                        Button(onClick = { startActivity(Intent(Settings.ACTION_SETTINGS)) }) { Text("Open Android Settings") }
                        Button(onClick = {
                            startActivity(Intent(Intent.ACTION_DIAL))
                        }) { Text("Open Phone") }
                        Button(onClick = { state.repairDisplay(); tick++ }) { Text("Restore recorded display baseline") }
                        Text("Recovery: disable the accessibility service, choose another Home app in Android Settings, and run scripts/recover-display.ps1 with your saved baseline. Force-stop/crash cleanup is not guaranteed.")
                        if (state.display.active()) {
                            Text("A display baseline journal is pending. Before changing color settings, review recovery instructions.")
                            DisplayJournal.transform.keys.forEach { key -> Text("$key: ${state.store.get("baseline:$key") ?: "unknown"}") }
                        }
                        Text("Configured apps without a usable launcher entry:")
                        state.catalog.missing().forEach { Text("${it.name}\n${it.packageName}") }
                        Text("Installed labels for the two package-only Secondary entries:")
                        state.catalog.entries.filter { it.packageName in setOf("com.cincsystems.ppm1", "com.comit.caregiver") }
                            .forEach { Text("${it.packageName}: ${it.label}") }
                        Button(onClick = {
                            startActivity(Intent(this@SetupActivity, MainActivity::class.java)); finish()
                        }) { Text("Continue to Home") }
                    }
                }
            }
        }
    }
}
