package com.fahimc.kiddayboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DailyTadaRoot()
        }
    }
}

data class KidTask(
    val id: String,
    val title: String,
    val day: DayOfWeek,
    val completed: Boolean = false
)

private enum class BoardMode { Today, Week }
private enum class VoiceAction { Hello, Speak }

@Composable
private fun DailyTadaRoot() {
    var showSplash by remember { mutableStateOf(true) }
    val splashAlpha = remember { Animatable(1f) }
    val logoScale = remember { Animatable(0.88f) }

    LaunchedEffect(Unit) {
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
        )
        delay(820)
        splashAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 420, easing = LinearEasing)
        )
        showSplash = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        KidDayBoardApp()
        if (showSplash) {
            DailyTadaSplash(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = splashAlpha.value
                        scaleX = 0.98f + logoScale.value * 0.02f
                        scaleY = 0.98f + logoScale.value * 0.02f
                    },
                logoScale = logoScale.value
            )
        }
    }
}

@Composable
private fun DailyTadaSplash(modifier: Modifier = Modifier, logoScale: Float) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFFE8F8FF),
                    Color(0xFFFFFBEC),
                    Color(0xFFD7F7EF)
                )
            )
        )
    ) {
        SplashDecorations(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DailyTadaLogoMark(
                modifier = Modifier
                    .size(222.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                    }
            )
            Spacer(modifier = Modifier.height(16.dp))
            DailyTadaWordmark()
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Little tasks, big ta-da!",
                color = Color(0xFF103E68),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SplashDecorations(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val teal = Color(0xFF12BFB6)
        val yellow = Color(0xFFFFC21C)
        val blue = Color(0xFF1C95EA)
        val coral = Color(0xFFFF5A62)

        drawCircle(Color.White.copy(alpha = 0.78f), radius = size.width * 0.17f, center = Offset(-size.width * 0.02f, size.height * 0.10f))
        drawCircle(Color.White.copy(alpha = 0.58f), radius = size.width * 0.13f, center = Offset(size.width * 1.03f, size.height * 0.06f))
        drawCircle(teal.copy(alpha = 0.18f), radius = size.width * 0.09f, center = Offset(size.width * 0.16f, size.height * 0.28f))
        drawCircle(coral.copy(alpha = 0.22f), radius = size.width * 0.08f, center = Offset(size.width * 0.86f, size.height * 0.32f))
        drawCircle(yellow.copy(alpha = 0.28f), radius = size.width * 0.06f, center = Offset(size.width * 0.28f, size.height * 0.83f))

        drawPath(starPath(size.width * 0.20f, size.height * 0.16f, 22f, 11f), color = yellow.copy(alpha = 0.58f))
        drawPath(starPath(size.width * 0.82f, size.height * 0.22f, 18f, 8f), color = teal.copy(alpha = 0.58f))
        drawPath(starPath(size.width * 0.49f, size.height * 0.88f, 20f, 9f), color = Color.White.copy(alpha = 0.88f))

        drawCircle(teal.copy(alpha = 0.75f), radius = 8f, center = Offset(size.width * 0.66f, size.height * 0.13f))
        drawCircle(blue.copy(alpha = 0.72f), radius = 6f, center = Offset(size.width * 0.24f, size.height * 0.39f))
        drawCircle(coral.copy(alpha = 0.72f), radius = 7f, center = Offset(size.width * 0.74f, size.height * 0.70f))

        drawArc(
            color = coral.copy(alpha = 0.24f),
            startAngle = 205f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(size.width * 0.66f, size.height * 0.76f),
            size = Size(size.width * 0.56f, size.width * 0.56f),
            style = Stroke(width = 18f, cap = StrokeCap.Round)
        )
        drawArc(
            color = yellow.copy(alpha = 0.26f),
            startAngle = 205f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(size.width * 0.70f, size.height * 0.79f),
            size = Size(size.width * 0.48f, size.width * 0.48f),
            style = Stroke(width = 16f, cap = StrokeCap.Round)
        )
        drawArc(
            color = teal.copy(alpha = 0.26f),
            startAngle = 205f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(size.width * 0.74f, size.height * 0.82f),
            size = Size(size.width * 0.40f, size.width * 0.40f),
            style = Stroke(width = 14f, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun DailyTadaLogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val teal = Color(0xFF10BEB8)
        val tealDark = Color(0xFF079B9F)
        val navy = Color(0xFF07335E)
        val yellow = Color(0xFFFFC21C)
        val coral = Color(0xFFFF5A62)
        val blue = Color(0xFF1C95EA)
        val w = size.width
        val h = size.height

        drawRoundRect(
            color = tealDark.copy(alpha = 0.18f),
            topLeft = Offset(w * 0.17f, h * 0.15f + 7f),
            size = Size(w * 0.54f, h * 0.66f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f, w * 0.10f)
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(teal, tealDark)),
            topLeft = Offset(w * 0.15f, h * 0.13f),
            size = Size(w * 0.58f, h * 0.66f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f, w * 0.10f)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.20f, h * 0.20f),
            size = Size(w * 0.48f, h * 0.53f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.07f, w * 0.07f)
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF19D2C7), Color(0xFF11B3B0))),
            topLeft = Offset(w * 0.31f, h * 0.08f),
            size = Size(w * 0.27f, h * 0.13f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, w * 0.05f)
        )

        drawArc(
            color = navy,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.31f, h * 0.31f),
            size = Size(w * 0.12f, h * 0.10f),
            style = Stroke(width = w * 0.025f, cap = StrokeCap.Round)
        )
        drawArc(
            color = navy,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.50f, h * 0.31f),
            size = Size(w * 0.12f, h * 0.10f),
            style = Stroke(width = w * 0.025f, cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = navy,
            topLeft = Offset(w * 0.42f, h * 0.41f),
            size = Size(w * 0.13f, h * 0.08f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f, w * 0.03f)
        )
        drawArc(
            color = coral,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.45f, h * 0.46f),
            size = Size(w * 0.07f, h * 0.04f)
        )

        drawTaskRow(Offset(w * 0.28f, h * 0.55f), teal, w)
        drawTaskRow(Offset(w * 0.28f, h * 0.64f), yellow, w)
        drawTaskRow(Offset(w * 0.28f, h * 0.73f), coral, w)

        drawCircle(teal, radius = w * 0.047f, center = Offset(w * 0.78f, h * 0.18f))
        drawRoundRect(blue, topLeft = Offset(w * 0.76f, h * 0.36f), size = Size(w * 0.05f, h * 0.19f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.025f, w * 0.025f))
        drawRoundRect(yellow, topLeft = Offset(w * 0.82f, h * 0.25f), size = Size(w * 0.05f, h * 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.025f, w * 0.025f))
        drawRoundRect(coral, topLeft = Offset(w * 0.86f, h * 0.43f), size = Size(w * 0.05f, h * 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.025f, w * 0.025f))

        val star = starPath(w * 0.72f, h * 0.66f, w * 0.19f, w * 0.095f)
        drawPath(star, color = Color.White)
        drawPath(starPath(w * 0.72f, h * 0.65f, w * 0.17f, w * 0.085f), color = yellow)
        drawLine(
            color = Color.White,
            start = Offset(w * 0.66f, h * 0.66f),
            end = Offset(w * 0.70f, h * 0.71f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(w * 0.70f, h * 0.71f),
            end = Offset(w * 0.79f, h * 0.59f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTaskRow(center: Offset, color: Color, width: Float) {
    drawCircle(color = color, radius = width * 0.045f, center = center)
    drawLine(
        color = Color.White,
        start = Offset(center.x - width * 0.020f, center.y),
        end = Offset(center.x - width * 0.004f, center.y + width * 0.018f),
        strokeWidth = width * 0.013f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White,
        start = Offset(center.x - width * 0.004f, center.y + width * 0.018f),
        end = Offset(center.x + width * 0.026f, center.y - width * 0.022f),
        strokeWidth = width * 0.013f,
        cap = StrokeCap.Round
    )
    drawRoundRect(
        color = color.copy(alpha = 0.30f),
        topLeft = Offset(center.x + width * 0.07f, center.y - width * 0.014f),
        size = Size(width * 0.18f, width * 0.028f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width * 0.014f, width * 0.014f)
    )
}

@Composable
private fun DailyTadaWordmark() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Daily", color = Color(0xFF10BEB8), fontSize = 44.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("T", color = Color(0xFF1C95EA), fontSize = 44.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("a", color = Color(0xFFFFC21C), fontSize = 44.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("d", color = Color(0xFFFF5A62), fontSize = 44.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text("a", color = Color(0xFF10BEB8), fontSize = 44.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun starPath(centerX: Float, centerY: Float, outerRadius: Float, innerRadius: Float): Path {
    val path = Path()
    repeat(10) { index ->
        val radius = if (index % 2 == 0) outerRadius else innerRadius
        val angle = -Math.PI / 2.0 + index * Math.PI / 5.0
        val x = centerX + cos(angle).toFloat() * radius
        val y = centerY + sin(angle).toFloat() * radius
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private interface ChildCoach {
    suspend fun greet(childName: String, tasks: List<KidTask>): String
    suspend fun handleChildReply(childName: String, reply: String, tasks: List<KidTask>): CoachResult
}

private data class CoachResult(
    val message: String,
    val completedTaskIds: Set<String> = emptySet()
)

private class HybridChildCoach(private val context: Context) : ChildCoach {
    private val fallback = RuleBasedChildCoach()
    private val gemma = LiteRtGemmaCoach(context, fallback)

    override suspend fun greet(childName: String, tasks: List<KidTask>): String = gemma.greet(childName, tasks)

    override suspend fun handleChildReply(childName: String, reply: String, tasks: List<KidTask>): CoachResult {
        val result = fallback.handleChildReply(childName, reply, tasks)
        val aiMessage = gemma.handleChildReply(childName, reply, tasks).message
        return result.copy(message = aiMessage)
    }
}

private class RuleBasedChildCoach : ChildCoach {
    override suspend fun greet(childName: String, tasks: List<KidTask>): String {
        val name = childName.childAddress()
        val remaining = tasks.filterNot { it.completed }
        if (remaining.isEmpty()) {
            return "Hello $name. Everything is finished for today. Time for a celebration."
        }
        val taskText = remaining.joinToString(", ") { it.title }
        return "Hello $name. Today you still have ${remaining.size} task${if (remaining.size == 1) "" else "s"}: $taskText. Which one have you completed?"
    }

    override suspend fun handleChildReply(childName: String, reply: String, tasks: List<KidTask>): CoachResult {
        val name = childName.childAddress()
        val remaining = tasks.filterNot { it.completed }

        val lower = reply.lowercase(Locale.getDefault())
        val taskUpdate = looksLikeTaskUpdate(lower)
        val completeAll = (taskUpdate && listOf("all done", "finished all", "completed all").any(lower::contains)) ||
            lower.trim() == "everything"
        val completed = if (completeAll) {
            remaining.map { it.id }.toSet()
        } else {
            remaining.filter { task ->
                task.title.lowercase(Locale.getDefault())
                    .split(" ", "-", "_")
                    .filter { it.length > 2 }
                    .any { lower.contains(it) }
            }.map { it.id }.toSet()
        }

        return when {
            completed.isEmpty() && taskUpdate -> {
                CoachResult("Thanks, $name. I did not catch which task is finished yet. Try saying the task name.")
            }
            completed.isEmpty() -> CoachResult(genericKidSafeReply(name, lower, remaining))
            completed.size == remaining.size -> CoachResult("Amazing, $name. I marked everything as complete.", completed)
            else -> {
                val names = remaining.filter { it.id in completed }.joinToString(", ") { it.title }
                CoachResult("Nice, $name. I marked $names as complete. Keep going.", completed)
            }
        }
    }

    private fun looksLikeTaskUpdate(text: String): Boolean {
        val taskWords = listOf(
            "done",
            "finished",
            "complete",
            "completed",
            "did it",
            "i did",
            "i have",
            "marked",
            "task"
        )
        return taskWords.any(text::contains)
    }

    private fun genericKidSafeReply(name: String, text: String, remaining: List<KidTask>): String {
        val remainingText = if (remaining.isEmpty()) {
            "Your tasks are finished."
        } else {
            "You still have ${remaining.size} task${if (remaining.size == 1) "" else "s"} left."
        }
        return when {
            text.contains("hello") || text.contains("hi") -> "Hello $name. It is nice to talk with you. $remainingText"
            text.contains("thank") -> "You are welcome, $name. I am happy to help."
            text.contains("joke") -> "Here is a gentle one, $name: why did the star smile? Because it finished shining."
            text.contains("?") ||
                text.startsWith("what") ||
                text.startsWith("why") ||
                text.startsWith("how") ||
                text.startsWith("who") ||
                text.startsWith("when") ||
                text.startsWith("where") -> {
                "Good question, $name. I will keep answers simple and kind, and I will say if I do not know."
            }
            else -> "Thanks for telling me, $name. I will keep things simple, kind, and safe."
        }
    }
}

private class LiteRtGemmaCoach(
    private val context: Context,
    private val fallback: RuleBasedChildCoach
) : ChildCoach {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val modelFile: File
        get() = File(context.getExternalFilesDir(null), "models/gemma-4-E2B-it.litertlm")

    override suspend fun greet(childName: String, tasks: List<KidTask>): String {
        if (!modelFile.exists()) return fallback.greet(childName, tasks)
        return generate(
            "Greet ${childName.childAddress()} warmly by name. Ask about the unfinished tasks only.\n${taskContext(tasks)}"
        ) ?: fallback.greet(childName, tasks)
    }

    override suspend fun handleChildReply(childName: String, reply: String, tasks: List<KidTask>): CoachResult {
        if (!modelFile.exists()) return fallback.handleChildReply(childName, reply, tasks)
        val prompt = """
            The child is ${childName.childAddress()}.
            ${childName.childAddress()} said: "$reply"
            Reply in one short, friendly spoken sentence and use the child's name naturally.
            If this is a general question, answer in a kid-friendly and generic way only when you know.
            If you are not sure, say you do not know instead of making anything up.
            Keep the answer safe, kind, age-appropriate, and under 25 words.
            Do not claim a task is complete unless the child clearly says it is done.
            ${taskContext(tasks)}
        """.trimIndent()
        val message = generate(prompt) ?: fallback.handleChildReply(childName, reply, tasks).message
        return CoachResult(message)
    }

    private fun taskContext(tasks: List<KidTask>): String {
        val remaining = tasks.filterNot { it.completed }.joinToString("; ") { "${it.day}: ${it.title}" }
        val completed = tasks.filter { it.completed }.joinToString("; ") { "${it.day}: ${it.title}" }
        return "Remaining tasks: ${remaining.ifBlank { "none" }}. Completed tasks: ${completed.ifBlank { "none" }}."
    }

    private suspend fun generate(prompt: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val activeConversation = ensureConversation()
            suspendCancellableCoroutine { continuation ->
                val builder = StringBuilder()
                activeConversation.sendMessageAsync(prompt, object : MessageCallback {
                    override fun onMessage(message: Message) {
                        builder.append(message.toString())
                    }

                    override fun onDone() {
                        continuation.resume(builder.toString().trim().ifBlank { null })
                    }

                    override fun onError(throwable: Throwable) {
                        continuation.resume(null)
                    }
                })
            }
        }.getOrNull()
    }

    private fun ensureConversation(): Conversation {
        conversation?.let { return it }
        val config = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath
        )
        val activeEngine = Engine(config).also {
            engine = it
            it.initialize()
        }
        return activeEngine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "You are a kind children's task coach. You may answer general child-safe questions. Never make facts up; if unsure, say you do not know. Keep replies generic, warm, safe, age-appropriate, and concise."
                ),
                samplerConfig = SamplerConfig(topK = 12, topP = 0.9, temperature = 0.65)
            )
        ).also { conversation = it }
    }
}

@Composable
private fun KidDayBoardApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now().dayOfWeek }
    val storage = remember { TaskStorage(context) }
    val tasks = remember { mutableStateListOf<KidTask>().apply { addAll(storage.loadTasks()) } }
    var childName by remember { mutableStateOf(storage.loadChildName()) }
    var selectedDay by remember { mutableStateOf(today) }
    var newTask by remember { mutableStateOf("") }
    var coachMessage by remember { mutableStateOf("Tap Hello when everyone is ready.") }
    var listening by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var voiceAction by remember { mutableStateOf(VoiceAction.Hello) }
    var voiceIdleTick by remember { mutableIntStateOf(0) }
    var celebrateCount by remember { mutableIntStateOf(0) }
    val coach = remember { HybridChildCoach(context) }
    val speech = remember { DailyTadaSpeechOutput(context) }
    val microphone = remember { InAppMicrophone() }
    val transcriber = remember { LocalWhisperTranscriber(context) }
    var listeningJob by remember { mutableStateOf<Job?>(null) }
    var processingSpeech by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            microphone.stopRecording()
            listeningJob?.cancel()
            speech.shutdown()
        }
    }

    LaunchedEffect(tasks.map { it.id to it.completed }) {
        storage.saveTasks(tasks)
        if (tasks.any { it.day == today } && tasks.filter { it.day == today }.all { it.completed }) {
            celebrateCount++
        }
    }

    LaunchedEffect(childName) {
        storage.saveChildName(childName)
    }

    LaunchedEffect(voiceAction, voiceIdleTick) {
        if (voiceAction == VoiceAction.Speak) {
            delay(45_000)
            voiceAction = VoiceAction.Hello
            listening = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) coachMessage = "Microphone permission is needed for the speak button."
    }

    MaterialTheme(colorScheme = kidColors()) {
        Surface(color = Color(0xFF8AD6CD), modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                val todayTasks = tasks.filter { it.day == today }
                KidStoryCard(
                    childName = childName,
                    tasks = todayTasks,
                    voiceAction = voiceAction,
                    listening = listening,
                    processingSpeech = processingSpeech,
                    onSettings = { showSettings = true },
                    onHello = {
                        voiceAction = VoiceAction.Speak
                        voiceIdleTick++
                        scope.launch {
                            val text = coach.greet(childName, tasks.filter { it.day == today })
                            coachMessage = text
                            speech.speak(text)
                        }
                    },
                    onSpeak = {
                        if (listening) {
                            microphone.stopRecording()
                            listening = false
                            voiceIdleTick++
                        } else {
                            voiceIdleTick++
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                listeningJob = scope.launch {
                                    listening = true
                                    processingSpeech = false
                                    try {
                                        val audio = microphone.recordClip()
                                        listening = false
                                        processingSpeech = true
                                        val transcription = transcriber.transcribe(audio)
                                        if (transcription.text.isNullOrBlank()) {
                                            coachMessage = transcription.userMessage
                                            speech.speak(transcription.userMessage)
                                        } else {
                                            val answer = coach.handleChildReply(childName, transcription.text, todayTasks)
                                            if (answer.completedTaskIds.isNotEmpty()) {
                                                answer.completedTaskIds.forEach { id ->
                                                    val index = tasks.indexOfFirst { it.id == id }
                                                    if (index >= 0) tasks[index] = tasks[index].copy(completed = true)
                                                }
                                            }
                                            coachMessage = answer.message
                                            speech.speak(answer.message)
                                        }
                                    } catch (_: CancellationException) {
                                        coachMessage = "Stopped listening."
                                    } finally {
                                        listening = false
                                        processingSpeech = false
                                        listeningJob = null
                                        voiceIdleTick++
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                )

                if (showSettings) {
                    SettingsDialog(
                        childName = childName,
                        selectedDay = selectedDay,
                        tasks = tasks,
                        newTask = newTask,
                        voiceLabel = speech.voiceLabel,
                        onChildNameChange = { childName = it },
                        onDaySelected = { selectedDay = it },
                        onNewTaskChange = { newTask = it },
                        onAdd = {
                            val clean = newTask.trim()
                            if (clean.isNotBlank()) {
                                tasks.add(KidTask(UUID.randomUUID().toString(), clean, selectedDay))
                                newTask = ""
                            }
                        },
                        onToggle = { task ->
                            val index = tasks.indexOfFirst { it.id == task.id }
                            if (index >= 0) tasks[index] = task.copy(completed = !task.completed)
                        },
                        onRemove = { task -> tasks.removeAll { it.id == task.id } },
                        onTryVoice = { speech.speak("Hello ${childName.childAddress()}. This is the best voice I can find on this device.") },
                        onClose = { showSettings = false }
                    )
                }

                CelebrationOverlay(trigger = celebrateCount)
            }
        }
    }
}

@Composable
private fun KidStoryCard(
    childName: String,
    tasks: List<KidTask>,
    voiceAction: VoiceAction,
    listening: Boolean,
    processingSpeech: Boolean,
    onSettings: () -> Unit,
    onHello: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val done = tasks.count { it.completed }
    val total = tasks.size
    val name = childName.trim().ifBlank { "Friend" }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = Color.White,
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
        ) {
            HeroPanel(
                childName = name,
                onSettings = onSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
            )
            WhiteWaveBackground(modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(236.dp))
                VoiceCircleButton(
                    action = voiceAction,
                    listening = listening,
                    processingSpeech = processingSpeech,
                    onHello = onHello,
                    onSpeak = onSpeak
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "$name's tasks",
                    color = Color(0xFF24233A),
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (total == 0) "All clear today" else "$done of $total complete",
                    color = Color(0xFF8F95A8),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                DailyTaskStrips(tasks = tasks, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HeroPanel(childName: String, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(Color(0xFFE7FBF8), Color(0xFFC9F0F2), Color(0xFFB7B3F4)),
                start = Offset(0f, 0f),
                end = Offset(900f, 700f)
            )
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(Color(0x55FFFFFF), radius = w * 0.12f, center = Offset(w * 0.17f, h * 0.25f))
            drawCircle(Color(0x44F7A9D9), radius = w * 0.08f, center = Offset(w * 0.82f, h * 0.24f))
            drawCircle(Color(0x334FD3C6), radius = w * 0.09f, center = Offset(w * 0.74f, h * 0.57f))

            repeat(7) { index ->
                val baseX = w * (0.12f + index * 0.13f)
                val baseY = h * (0.78f + (index % 2) * 0.06f)
                drawRoundRect(
                    color = Color(0x8861C9B8),
                    topLeft = Offset(baseX, baseY - 70f),
                    size = Size(18f, 78f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
                )
                drawCircle(Color(0x887A77D9), radius = 24f, center = Offset(baseX + 8f, baseY - 62f))
                drawCircle(Color(0x7757D8C6), radius = 20f, center = Offset(baseX - 16f, baseY - 35f))
            }

            val heart = Path().apply {
                moveTo(w * 0.50f, h * 0.43f)
                cubicTo(w * 0.34f, h * 0.27f, w * 0.20f, h * 0.48f, w * 0.50f, h * 0.68f)
                cubicTo(w * 0.80f, h * 0.48f, w * 0.66f, h * 0.27f, w * 0.50f, h * 0.43f)
                close()
            }
            drawPath(
                path = heart,
                brush = Brush.verticalGradient(listOf(Color(0xFFFF90C6), Color(0xFF7D67E8)))
            )

            drawCircle(Color(0xFFFFC36F), radius = w * 0.11f, center = Offset(w * 0.34f, h * 0.54f))
            drawCircle(Color(0xFFFFB65B), radius = w * 0.11f, center = Offset(w * 0.66f, h * 0.54f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = Color(0xFF23233A), fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x99FFFFFF))
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) {
                Text("≡", color = Color(0xFF23233A), fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
        }

        Text(
            text = childName.take(1).uppercase(Locale.getDefault()).ifBlank { "K" },
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 16.dp)
                .clip(CircleShape)
                .background(Color(0x664FD3C6))
                .padding(horizontal = 22.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun WhiteWaveBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val top = size.height * 0.34f
        val path = Path().apply {
            moveTo(0f, top + 70f)
            cubicTo(size.width * 0.24f, top - 38f, size.width * 0.76f, top - 38f, size.width, top + 70f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path = path, color = Color.White)
    }
}

@Composable
private fun VoiceCircleButton(
    action: VoiceAction,
    listening: Boolean,
    processingSpeech: Boolean,
    onHello: () -> Unit,
    onSpeak: () -> Unit
) {
    val active = action == VoiceAction.Speak
    val isHello = !active && !listening && !processingSpeech
    val pulse = rememberInfiniteTransition(label = "voice-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice-pulse-scale"
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice-pulse-alpha"
    )
    val spinnerRotation by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing)
        ),
        label = "voice-spinner"
    )
    val buttonColors = when {
        listening -> listOf(Color(0xFFFF4E58), Color(0xFFFF3040))
        processingSpeech -> listOf(Color(0xFF8F7BFF), Color(0xFF5D7AF0))
        active -> listOf(Color(0xFF4FD3C6), Color(0xFF48A6F0))
        else -> listOf(Color(0xFFFFC45C), Color(0xFFFF7C8C))
    }
    val outerColor = when {
        listening -> Color(0xFF3F3F3F)
        processingSpeech -> Color(0xFF5E5A7C)
        isHello -> Color(0xFF70516E)
        else -> Color(0xFF4C6166)
    }
    val label = when {
        listening -> "Stop"
        processingSpeech -> "Wait"
        active -> "Speak"
        else -> "Hello"
    }
    Box(
        modifier = Modifier
            .size(132.dp),
        contentAlignment = Alignment.Center
    ) {
        if (listening || processingSpeech) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val base = size.minDimension / 2f
                drawCircle(
                    color = buttonColors.first().copy(alpha = pulseAlpha),
                    radius = base * pulseScale
                )
            }
        }
        Box(
            modifier = Modifier
                .size(118.dp)
                .clip(CircleShape)
                .background(outerColor.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = buttonColors
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                    .clickable {
                        if (!processingSpeech) {
                            if (active) onSpeak() else onHello()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (!isHello) {
                        Canvas(modifier = Modifier.size(if (processingSpeech) 34.dp else 30.dp)) {
                            if (processingSpeech) {
                                rotate(spinnerRotation) {
                                    drawArc(
                                        color = Color.White.copy(alpha = 0.32f),
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
                                        size = Size(size.width * 0.84f, size.height * 0.84f),
                                        style = Stroke(width = size.width * 0.12f, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = Color.White,
                                        startAngle = -90f,
                                        sweepAngle = 120f,
                                        useCenter = false,
                                        topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
                                        size = Size(size.width * 0.84f, size.height * 0.84f),
                                        style = Stroke(width = size.width * 0.12f, cap = StrokeCap.Round)
                                    )
                                }
                            } else {
                                val centerX = size.width / 2f
                                val top = size.height * 0.08f
                                val micWidth = size.width * 0.34f
                                val micHeight = size.height * 0.50f
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(centerX - micWidth / 2f, top),
                                    size = Size(micWidth, micHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(micWidth / 2f, micWidth / 2f)
                                )
                                drawArc(
                                    color = Color.White,
                                    startAngle = 0f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = Offset(size.width * 0.17f, size.height * 0.28f),
                                    size = Size(size.width * 0.66f, size.height * 0.52f),
                                    style = Stroke(width = size.width * 0.12f, cap = StrokeCap.Round)
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(centerX, size.height * 0.73f),
                                    end = Offset(centerX, size.height * 0.92f),
                                    strokeWidth = size.width * 0.12f,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(centerX - size.width * 0.16f, size.height * 0.92f),
                                    end = Offset(centerX + size.width * 0.16f, size.height * 0.92f),
                                    strokeWidth = size.width * 0.12f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = label.uppercase(Locale.getDefault()),
                        color = Color.White,
                        fontSize = if (isHello) 15.sp else 11.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (!listening && !processingSpeech) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.14f),
                    radius = size.minDimension * 0.47f,
                    style = Stroke(width = 5f)
                )
            }
        }
    }
}

@Composable
private fun DailyTaskStrips(tasks: List<KidTask>, modifier: Modifier = Modifier) {
    if (tasks.isEmpty()) {
        Box(
            modifier = modifier
                .height(96.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFF5F6FB)),
            contentAlignment = Alignment.Center
        ) {
            Text("No tasks today", color = Color(0xFF8F95A8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tasks, key = { it.id }) { task ->
            DailyTaskStrip(task)
        }
    }
}

@Composable
private fun DailyTaskStrip(task: KidTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (task.completed) {
                    Brush.linearGradient(listOf(Color(0xFF6FE5BF), Color(0xFF4FD3C6)))
                } else {
                    Brush.linearGradient(listOf(Color(0xFF5C7EF4), Color(0xFF32D0C7)))
                }
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (task.completed) "✓" else "•", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(task.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (task.completed) "Completed" else "Ready", color = Color(0xDFFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text("★", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Header(childName: String, tasks: List<KidTask>, onSettings: () -> Unit) {
    val total = tasks.size.coerceAtLeast(1)
    val done = tasks.count { it.completed }
    val title = if (childName.trim().isNotBlank()) "${childName.trim()}'s day" else "Today"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color(0xFF14213D))
            Text("Finish the board together.", color = Color(0xFF51606F), fontSize = 15.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onSettings) {
                Text("Settings", fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2B6CB0)),
                contentAlignment = Alignment.Center
            ) {
                Text("$done/$total", color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
            }
        }
    }
}

@Composable
private fun ChildTaskList(tasks: List<KidTask>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE2DED3), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No tasks today", color = Color(0xFF51606F), fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            return@Column
        }

        tasks.forEach { task ->
            ChildTaskCard(task)
        }
    }
}

@Composable
private fun ChildTaskCard(task: KidTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(if (task.completed) Color(0xFFE8F6EF) else Color.White)
            .border(1.dp, if (task.completed) Color(0xFF9BD8B6) else Color(0xFFE2DED3), RoundedCornerShape(26.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (task.completed) Color(0xFF248A52) else Color(0xFFF6C453)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (task.completed) "OK" else "GO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            task.title,
            color = Color(0xFF14213D),
            fontWeight = FontWeight.Black,
            fontSize = 23.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (task.completed) {
            Text(
                "Done",
                color = Color(0xFF176B3A),
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFCFF2DC))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    childName: String,
    selectedDay: DayOfWeek,
    tasks: List<KidTask>,
    newTask: String,
    voiceLabel: String,
    onChildNameChange: (String) -> Unit,
    onDaySelected: (DayOfWeek) -> Unit,
    onNewTaskChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (KidTask) -> Unit,
    onRemove: (KidTask) -> Unit,
    onTryVoice: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            Button(onClick = onClose) {
                Text("Done")
            }
        },
        title = {
            Text("Week setup", fontWeight = FontWeight.Black)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = childName,
                    onValueChange = onChildNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Child's name") }
                )
                DayPicker(selectedDay = selectedDay, onSelected = onDaySelected)
                AddTaskRow(
                    selectedDay = selectedDay,
                    text = newTask,
                    onTextChange = onNewTaskChange,
                    onAdd = onAdd
                )
                Text(
                    "Voice: $voiceLabel",
                    color = Color(0xFF51606F),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                OutlinedButton(onClick = onTryVoice) {
                    Text("Try voice")
                }
                Box(modifier = Modifier.height(260.dp)) {
                    TaskList(
                        mode = BoardMode.Week,
                        tasks = tasks.filter { it.day == selectedDay }.sortedBy { it.title },
                        onToggle = onToggle,
                        onRemove = onRemove
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFF8F4EA)
    )
}

@Composable
private fun ModeSwitch(mode: BoardMode, onModeChange: (BoardMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2DED3), RoundedCornerShape(28.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BoardMode.entries.forEach { item ->
            val selected = item == mode
            Text(
                text = if (item == BoardMode.Today) "Day" else "Week",
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (selected) Color(0xFF14213D) else Color.Transparent)
                    .clickable { onModeChange(item) }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                color = if (selected) Color.White else Color(0xFF14213D),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayPicker(selectedDay: DayOfWeek, onSelected: (DayOfWeek) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day == selectedDay,
                onClick = { onSelected(day) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
            )
        }
    }
}

@Composable
private fun VoiceActionButton(
    action: VoiceAction,
    listening: Boolean,
    onHello: () -> Unit,
    onSpeak: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (action == VoiceAction.Hello) {
            Button(
                onClick = onHello,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B6CB0)),
                modifier = Modifier
                    .height(66.dp)
                    .width(180.dp)
            ) {
                Text("Hello", fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
        } else {
            Button(
                onClick = onSpeak,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF248A52)),
                modifier = Modifier
                    .height(66.dp)
                    .width(180.dp)
            ) {
                Text(if (listening) "Listening" else "Speak", fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun AddTaskRow(
    selectedDay: DayOfWeek,
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("Add ${selectedDay.getDisplayName(TextStyle.SHORT, Locale.getDefault())} task") }
        )
        Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF248A52))) {
            Text("Add")
        }
    }
}

@Composable
private fun TaskList(
    mode: BoardMode,
    tasks: List<KidTask>,
    onToggle: (KidTask) -> Unit,
    onRemove: (KidTask) -> Unit
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text("No tasks yet", color = Color(0xFF51606F), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        items(tasks, key = { it.id }) { task ->
            TaskCard(mode, task, onToggle, onRemove)
        }
    }
}

@Composable
private fun TaskCard(
    mode: BoardMode,
    task: KidTask,
    onToggle: (KidTask) -> Unit,
    onRemove: (KidTask) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (task.completed) Color(0xFFE8F6EF) else Color.White)
            .border(1.dp, if (task.completed) Color(0xFF9BD8B6) else Color(0xFFE2DED3), RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = task.completed, onCheckedChange = { onToggle(task) })
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(task.title, color = Color(0xFF14213D), fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (mode == BoardMode.Week) {
                Text(task.day.getDisplayName(TextStyle.FULL, Locale.getDefault()), color = Color(0xFF51606F), fontSize = 13.sp)
            }
        }
        if (task.completed) {
            Text(
                "Completed",
                color = Color(0xFF176B3A),
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFCFF2DC))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        } else {
            Text(
                "Remove",
                color = Color(0xFFB04747),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onRemove(task) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun CelebrationOverlay(trigger: Int) {
    var visible by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            visible = true
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(1800, easing = LinearEasing))
            visible = false
        }
    }

    AnimatedVisibility(visible = visible) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val colors = listOf(
                Color(0xFFF6C453),
                Color(0xFF2B6CB0),
                Color(0xFF248A52),
                Color(0xFFE76F51)
            )
            repeat(52) { index ->
                val angle = index * 137.5f
                val radius = size.minDimension * (0.1f + progress.value * 0.75f)
                val x = size.width / 2 + cos(Math.toRadians(angle.toDouble())).toFloat() * radius
                val y = size.height / 2 + sin(Math.toRadians(angle.toDouble())).toFloat() * radius + progress.value * 160f
                rotate(angle + progress.value * 360f, pivot = Offset(x, y)) {
                    drawRoundRect(
                        color = colors[index % colors.size],
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(18f, 28f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                }
            }
        }
    }
}

private class DailyTadaSpeechOutput(context: Context) {
    private val appContext = context.applicationContext
    private val fallback = AndroidSpeechOutput(appContext)
    private val speechJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + speechJob)
    private var kitten: KittenOnnxSpeechEngine? = null
    private var kittenFailed = false
    var voiceLabel by mutableStateOf("Preparing KittenTTS ONNX")
        private set

    init {
        scope.launch {
            val result = runCatching { KittenOnnxSpeechEngine(appContext) }
            result.onSuccess { engine ->
                kitten = engine
                voiceLabel = "KittenTTS ONNX: nano v0.2 fp16, local"
            }.onFailure { error ->
                kittenFailed = true
                Log.w("DailyTadaSpeech", "KittenTTS ONNX unavailable; using Android TTS fallback", error)
                voiceLabel = "Android TTS fallback; KittenTTS unavailable"
            }
        }
    }

    fun speak(text: String) {
        val engine = kitten
        if (engine != null && engine.speak(text)) {
            return
        }
        fallback.speak(text)
        if (!kittenFailed && engine == null) {
            voiceLabel = "KittenTTS loading; Android TTS fallback"
        }
    }

    fun shutdown() {
        speechJob.cancel()
        kitten?.shutdown()
        fallback.shutdown()
    }
}

private class KittenOnnxSpeechEngine(private val context: Context) {
    private val modelDir = "models/kitten-nano-en-v0_2-fp16"
    private val dataDirAsset = "$modelDir/espeak-ng-data"
    private val defaultSpeakerId = 7
    private val generationSpeed = 1.02f
    private val lock = Any()
    private var activeToken = 0
    private var currentJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tts: OfflineTts
    private val track: AudioTrack

    init {
        context.assets.open("$modelDir/model.fp16.onnx").close()
        val copiedDataDir = copyAssetTree(dataDirAsset)
        tts = OfflineTts(
            assetManager = context.assets,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kitten = OfflineTtsKittenModelConfig(
                        model = "$modelDir/model.fp16.onnx",
                        voices = "$modelDir/voices.bin",
                        tokens = "$modelDir/tokens.txt",
                        dataDir = copiedDataDir.absolutePath,
                    ),
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
                silenceScale = 0.16f,
            )
        )
        track = createTrack(tts.sampleRate())
        track.play()
    }

    fun speak(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return true
        return runCatching {
            val token = synchronized(lock) {
                activeToken += 1
                currentJob?.cancel()
                prepareTrackLocked()
                activeToken
            }
            currentJob = scope.launch {
                val genConfig = GenerationConfig(
                    sid = defaultSpeakerId,
                    speed = generationSpeed,
                    silenceScale = 0.16f,
                )
                tts.generateWithConfigAndCallback(text = clean, config = genConfig) { samples ->
                    if (token != activeToken) {
                        0
                    } else {
                        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        1
                    }
                }
            }
            true
        }.getOrElse { error ->
            Log.w("DailyTadaSpeech", "KittenTTS ONNX speak failed", error)
            false
        }
    }

    fun shutdown() {
        synchronized(lock) {
            activeToken += 1
            currentJob?.cancel()
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
            runCatching { tts.release() }
        }
    }

    private fun prepareTrackLocked() {
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.play() }
    }

    private fun createTrack(sampleRate: Int): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(sampleRate / 2)
        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()
        return AudioTrack(
            attributes,
            format,
            minBufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    private fun copyAssetTree(assetPath: String): File {
        val root = File(context.filesDir, "sherpa-onnx-assets")
        val target = File(root, assetPath)
        val marker = File(target, ".copy-complete")
        if (marker.exists()) return target
        if (target.exists()) target.deleteRecursively()
        copyAssetRecursive(assetPath, root)
        marker.writeText("ok")
        return target
    }

    private fun copyAssetRecursive(assetPath: String, outputRoot: File) {
        val children = context.assets.list(assetPath).orEmpty()
        val target = File(outputRoot, assetPath)
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            return
        }
        target.mkdirs()
        children.forEach { child ->
            copyAssetRecursive("$assetPath/$child", outputRoot)
        }
    }
}

private class AndroidSpeechOutput(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    var voiceLabel by mutableStateOf("Preparing voice")
        private set
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            refreshVoice()
            tts.setSpeechRate(0.94f)
            tts.setPitch(1.12f)
        } else {
            voiceLabel = "Voice engine unavailable"
        }
    }

    fun refreshVoice() {
        if (!ready) return
        val bestVoice = tts.voices
            ?.filter { voice ->
                voice.locale.language == Locale.ENGLISH.language &&
                    voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
            ?.maxByOrNull { scoreVoice(it) }

        if (bestVoice != null) {
            tts.voice = bestVoice
            val network = if (bestVoice.isNetworkConnectionRequired) ", network enhanced" else ", offline"
            val preference = if (feminineVoiceBoost(bestVoice) > 0) "female voice" else "female preferred"
            voiceLabel = "${bestVoice.locale.displayName}: ${bestVoice.name}$network, $preference"
        } else {
            tts.language = Locale.UK
            voiceLabel = "Default English voice, female preferred"
        }
    }

    private fun scoreVoice(voice: Voice): Int {
        val preferredLocale = when (voice.locale.country.uppercase(Locale.US)) {
            "GB" -> 40
            "US" -> 30
            else -> 0
        }
        val networkBoost = if (voice.isNetworkConnectionRequired) 20 else 0
        return voice.quality * 100 - voice.latency * 4 + preferredLocale + networkBoost + feminineVoiceBoost(voice)
    }

    private fun feminineVoiceBoost(voice: Voice): Int {
        val searchable = buildString {
            append(voice.name.lowercase(Locale.US))
            append(' ')
            append(voice.locale.displayName.lowercase(Locale.US))
            append(' ')
            append(voice.features.orEmpty().joinToString(" ").lowercase(Locale.US))
        }
        val strongSignals = listOf("female", "feminine", "woman", "girl")
        if (strongSignals.any(searchable::contains)) return 500

        val commonFemaleVoiceNames = listOf(
            "allison",
            "alice",
            "amy",
            "aria",
            "ava",
            "emma",
            "hazel",
            "heera",
            "ivy",
            "joanna",
            "karen",
            "kendra",
            "kimberly",
            "libby",
            "linda",
            "moira",
            "natasha",
            "olivia",
            "salli",
            "samantha",
            "serena",
            "sonia",
            "sophie",
            "susan",
            "tessa",
            "veena",
            "victoria",
            "zira"
        )
        return if (commonFemaleVoiceNames.any(searchable::contains)) 320 else 0
    }

    fun speak(text: String) {
        if (ready) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coach-${System.currentTimeMillis()}")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

private data class AudioClip(
    val samples: ShortArray,
    val sampleRate: Int
)

private data class TranscriptionResult(
    val text: String?,
    val userMessage: String
)

private class InAppMicrophone {
    private val sampleRate = 16_000
    @Volatile
    private var stopRequested = false
    @Volatile
    private var activeRecorder: AudioRecord? = null

    fun stopRecording() {
        stopRequested = true
        runCatching { activeRecorder?.stop() }
    }

    @SuppressLint("MissingPermission")
    suspend fun recordClip(maxMillis: Long = 5_000): AudioClip = withContext(Dispatchers.IO) {
        stopRequested = false
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return@withContext AudioClip(shortArrayOf(), sampleRate)

        val readBuffer = ShortArray(minBuffer / 2)
        val samples = ArrayList<Short>(sampleRate * (maxMillis / 1000).toInt())
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )

        try {
            activeRecorder = recorder
            recorder.startRecording()
            val deadline = System.currentTimeMillis() + maxMillis
            while (System.currentTimeMillis() < deadline && !stopRequested && currentCoroutineContext().isActive) {
                val count = recorder.read(readBuffer, 0, readBuffer.size)
                if (count > 0) {
                    repeat(count) { index -> samples.add(readBuffer[index]) }
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            activeRecorder = null
            stopRequested = false
        }

        AudioClip(samples.toShortArray(), sampleRate)
    }
}

private class LocalWhisperTranscriber(private val context: Context) {
    private var model: WhisperModel? = null

    suspend fun transcribe(audioClip: AudioClip): TranscriptionResult = withContext(Dispatchers.IO) {
        if (audioClip.samples.isEmpty()) {
            return@withContext TranscriptionResult(
                text = null,
                userMessage = "I could not hear anything. Try again closer to the microphone."
            )
        }

        runCatching {
            val wavFile = writeWav(audioClip)
            val activeModel = ensureModel()
            val result = Whisper.transcribe(
                model = activeModel,
                audioPath = wavFile.absolutePath,
                config = WhisperConfig(
                    language = "auto",
                    translate = false,
                    threads = 4,
                    printTimestamps = false
                )
            )
            val clean = result.text.trim()
            TranscriptionResult(
                text = clean.ifBlank { null },
                userMessage = if (clean.isBlank()) {
                    "I did not catch that. Try again closer to the microphone."
                } else {
                    "I heard: $clean"
                }
            )
        }.getOrElse { error ->
            TranscriptionResult(
                text = null,
                userMessage = "Local Whisper could not understand that yet. ${error.message.orEmpty()}".trim()
            )
        }
    }

    private suspend fun ensureModel(): WhisperModel {
        model?.takeIf { it.isValid }?.let { return it }
        return Whisper.loadModelFromAsset(context, "models/ggml-tiny.en.bin").also { model = it }
    }

    private fun writeWav(audioClip: AudioClip): File {
        val file = File(context.cacheDir, "kid-day-board-speech.wav")
        FileOutputStream(file).use { output ->
            val dataBytes = audioClip.samples.size * 2
            output.writeAscii("RIFF")
            output.writeIntLe(36 + dataBytes)
            output.writeAscii("WAVE")
            output.writeAscii("fmt ")
            output.writeIntLe(16)
            output.writeShortLe(1)
            output.writeShortLe(1)
            output.writeIntLe(audioClip.sampleRate)
            output.writeIntLe(audioClip.sampleRate * 2)
            output.writeShortLe(2)
            output.writeShortLe(16)
            output.writeAscii("data")
            output.writeIntLe(dataBytes)
            audioClip.samples.forEach { sample -> output.writeShortLe(sample.toInt()) }
        }
        return file
    }
}

private class TaskStorage(context: Context) {
    private val preferences = context.getSharedPreferences("kid-day-board", Context.MODE_PRIVATE)

    fun loadChildName(): String = preferences.getString("childName", "").orEmpty()

    fun saveChildName(name: String) {
        preferences.edit().putString("childName", name.trim()).apply()
    }

    fun loadTasks(): List<KidTask> {
        val raw = preferences.getString("tasks", null) ?: return starterTasks()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                KidTask(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    day = DayOfWeek.valueOf(item.getString("day")),
                    completed = item.optBoolean("completed", false)
                )
            }
        }.getOrElse { starterTasks() }
    }

    fun saveTasks(tasks: List<KidTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("day", task.day.name)
                    .put("completed", task.completed)
            )
        }
        preferences.edit().putString("tasks", array.toString()).apply()
    }

    private fun starterTasks(): List<KidTask> {
        val today = LocalDate.now().dayOfWeek
        return listOf(
            KidTask(UUID.randomUUID().toString(), "Brush teeth", today),
            KidTask(UUID.randomUUID().toString(), "Pack school bag", today),
            KidTask(UUID.randomUUID().toString(), "Read for ten minutes", today.plusDays(1))
        )
    }
}

private fun String.childAddress(): String {
    return trim().ifBlank { "friend" }
}

private fun FileOutputStream.writeAscii(value: String) {
    write(value.toByteArray(Charsets.US_ASCII))
}

private fun FileOutputStream.writeIntLe(value: Int) {
    write(value and 0xff)
    write(value shr 8 and 0xff)
    write(value shr 16 and 0xff)
    write(value shr 24 and 0xff)
}

private fun FileOutputStream.writeShortLe(value: Int) {
    write(value and 0xff)
    write(value shr 8 and 0xff)
}

private fun DayOfWeek.plusDays(days: Long): DayOfWeek {
    val value = ((this.value - 1 + days) % 7).toInt()
    return DayOfWeek.of(value + 1)
}

@Composable
private fun kidColors() = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF2B6CB0),
    secondary = Color(0xFFF6C453),
    tertiary = Color(0xFF248A52),
    background = Color(0xFFF8F4EA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color(0xFF14213D),
    onTertiary = Color.White,
    onBackground = Color(0xFF14213D),
    onSurface = Color(0xFF14213D)
)
