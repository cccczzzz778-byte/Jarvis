package uz.jarvis.mobile

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class JarvisNotificationService : NotificationListenerService() {
    data class MessageSnapshot(
        val packageName: String,
        val title: String,
        val text: String,
        val postTime: Long
    )

    companion object {
        @Volatile private var lastMessage: MessageSnapshot? = null
        @Volatile private var lastReplyAction: Notification.Action? = null

        fun latest(): MessageSnapshot? = lastMessage
        fun hasReplyAction(): Boolean = lastReplyAction?.remoteInputs?.isNotEmpty() == true

        fun replyToLatest(text: String): Boolean {
            val action = lastReplyAction ?: return false
            val inputs = action.remoteInputs ?: return false
            if (inputs.isEmpty()) return false
            return try {
                val intent = Intent()
                val bundle = Bundle()
                inputs.forEach { input -> bundle.putCharSequence(input.resultKey, text) }
                RemoteInput.addResultsToIntent(inputs, intent, bundle)
                action.actionIntent.send(null, 0, intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val text = bigText.ifBlank { extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty() }
        if (title.isBlank() && text.isBlank()) return

        lastMessage = MessageSnapshot(sbn.packageName, title, text, sbn.postTime)
        lastReplyAction = notification.actions?.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        }
    }
}
