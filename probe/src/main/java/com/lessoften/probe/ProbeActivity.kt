package com.lessoften.probe

import android.app.*
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Bundle
import android.widget.*

/** Disposable, synthetic content for on-phone entry/destination/notification acceptance tests. */
class ProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); render() }
    private fun render() {
        val destination = intent.dataString ?: "generic-home"
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 100, 32, 32)
            addView(TextView(this@ProbeActivity).apply { text = "Destination: $destination"; textSize = 24f })
            addView(Button(this@ProbeActivity).apply {
                text = "Post native test notification"
                setOnClickListener { ProbeNotifications.post(this@ProbeActivity) }
            })
            addView(Button(this@ProbeActivity).apply {
                text = "Underlying touches: 0"
                var touches = 0
                setOnClickListener { touches++; text = "Underlying touches: $touches" }
            })
        })
    }
}

object ProbeNotifications {
    fun post(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("probe", "Less Often acceptance test", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, ProbeActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData(android.net.Uri.parse("lessoften-test://notification/42"))
        val pending = PendingIntent.getActivity(context, 42, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reply = PendingIntent.getBroadcast(context, 43, Intent(context, ReplyReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        manager.notify(42, Notification.Builder(context, "probe")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Less Often synthetic notification")
            .setContentText("Destination 42; no personal data").setContentIntent(pending)
            .addAction(Notification.Action.Builder(null, "Reply test", reply)
                .addRemoteInput(RemoteInput.Builder("reply").setLabel("Synthetic test reply").build()).build())
            .setAutoCancel(false).build())
    }
}

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (RemoteInput.getResultsFromIntent(intent)?.getCharSequence("reply") == null) return
        context.getSystemService(NotificationManager::class.java).notify(42, Notification.Builder(context, "probe")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Less Often synthetic notification")
            .setContentText("Inline reply received without opening the app").build())
    }
}

/** Real separate activity windows for detecting grayscale restoration during navigation. */
class ProbePageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val page = intent.getIntExtra("page", 1)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 160, 32, 32)
            addView(TextView(this@ProbePageActivity).apply { text = "Activity page: $page"; textSize = 24f })
            addView(Button(this@ProbePageActivity).apply {
                text = "Next activity"
                setOnClickListener { startActivity(Intent(this@ProbePageActivity, ProbePageActivity::class.java).putExtra("page", page + 1)) }
            })
            for (color in listOf(android.graphics.Color.RED, android.graphics.Color.GREEN, android.graphics.Color.BLUE)) {
                addView(android.view.View(this@ProbePageActivity).apply { setBackgroundColor(color) }, LinearLayout.LayoutParams(-1, 120))
            }
        })
    }
}
