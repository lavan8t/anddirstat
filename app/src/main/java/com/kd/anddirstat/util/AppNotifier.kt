package com.kd.anddirstat.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kd.anddirstat.MainActivity
import com.kd.anddirstat.R
import com.kd.anddirstat.ui.components.MaterialSymbol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.lang.reflect.Method

data class LiveActivityState(
    val isActive: Boolean = false,
    val title: String = "",
    val detail: String = "",
    val progress: Int = 0,
    val max: Int = 0,
    val isIndeterminate: Boolean = true,
    val type: String = "scan" // "scan" or "delete"
)

object AppNotifier {
    private const val CHANNEL_PROGRESS_ID = "anddirstat_silent_progress"
    private const val NOTIFICATION_ID = 2001

    private val _messages = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 10)
    val messages: kotlinx.coroutines.flow.SharedFlow<String> = _messages

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    private val _liveActivity = MutableStateFlow(LiveActivityState())
    val liveActivity: StateFlow<LiveActivityState> = _liveActivity.asStateFlow()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Background Tasks & Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent progress for storage scanning and file operations"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    // Android 16 Live Updates / Status Bar Chip support. Both setters are optional on older
    // platform versions, so they stay reflective - resolved once instead of per updateProgress call.
    private val setRequestPromotedOngoingMethod: Method? by lazy {
        try {
            NotificationCompat.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType!!)
        } catch (_: Exception) {
            null
        }
    }
    private val setShortCriticalTextMethod: Method? by lazy {
        try {
            NotificationCompat.Builder::class.java.getMethod("setShortCriticalText", CharSequence::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun updateProgress(
        context: Context,
        title: String,
        detail: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = true,
        type: String = "scan"
    ) {
        _liveActivity.value = LiveActivityState(
            isActive = true,
            title = title,
            detail = detail,
            progress = progress,
            max = max,
            isIndeterminate = indeterminate,
            type = type
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                return
            }

            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val shortText = if (type == "scan") "Scan" else "Delete"
            val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(detail)
                .setSubText(if (type == "scan") "Analyzing Storage" else "Deleting Files")
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            // Android 16 Live Updates / Status Bar Chip support
            try {
                setRequestPromotedOngoingMethod?.invoke(builder, true)
            } catch (_: Exception) {}

            try {
                setShortCriticalTextMethod?.invoke(builder, shortText)
            } catch (_: Exception) {}

            if (indeterminate || max <= 0) {
                builder.setProgress(0, 0, true)
            } else {
                builder.setProgress(max, progress, false)
            }

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }

    fun dismissAll(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancelAll()
        } catch (_: Exception) {}
    }

    fun finishActivity(context: Context, finalMessage: String? = null) {
        _liveActivity.value = LiveActivityState(isActive = false)
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
            if (!finalMessage.isNullOrBlank()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                }
                if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    return
                }

                val pi = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val doneNotification = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome)
                    .setContentTitle("Done")
                    .setContentText(finalMessage)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .build()
                nm?.notify(NOTIFICATION_ID + 1, doneNotification)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }
}
