package com.kd.anddirstat.util

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
                .setSmallIcon(R.mipmap.ic_launcher_round)
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
                val setRequestPromotedOngoingMethod = builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                setRequestPromotedOngoingMethod.invoke(builder, true)
            } catch (_: Exception) {}

            try {
                val setShortCriticalTextMethod = builder.javaClass.getMethod("setShortCriticalText", CharSequence::class.java)
                setShortCriticalTextMethod.invoke(builder, shortText)
            } catch (_: Exception) {}

            if (indeterminate || max <= 0) {
                builder.setProgress(0, 0, true)
            } else {
                builder.setProgress(max, progress, false)
            }

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}
    }

    fun finishActivity(context: Context, finalMessage: String? = null) {
        _liveActivity.value = LiveActivityState(isActive = false)
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
            if (!finalMessage.isNullOrBlank()) {
                val pi = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val doneNotification = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
                    .setSmallIcon(R.mipmap.ic_launcher_round)
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
        } catch (_: Exception) {}
    }
}

@Composable
fun LiveActivityPill(modifier: Modifier = Modifier) {
    val state by AppNotifier.liveActivity.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "liveActivityRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "rotation"
    )

    AnimatedVisibility(
        visible = state.isActive,
        enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(200)) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        ) {
                            val iconName = if (state.type == "scan") "sync" else "delete_sweep"
                            MaterialSymbol(
                                name = iconName,
                                active = true,
                                size = 16.dp,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .then(if (state.type == "scan") Modifier.rotate(rotation) else Modifier)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.detail.isNotEmpty()) {
                                Text(
                                    text = state.detail,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (state.isIndeterminate || state.max <= 0) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )
                } else {
                    val progressFraction = (state.progress.toFloat() / state.max.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}
