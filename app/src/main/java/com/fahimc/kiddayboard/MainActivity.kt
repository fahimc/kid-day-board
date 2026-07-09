package com.fahimc.kiddayboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
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
            KidDayBoardApp()
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

private interface ChildCoach {
    suspend fun greet(tasks: List<KidTask>): String
    suspend fun handleChildReply(reply: String, tasks: List<KidTask>): CoachResult
}

private data class CoachResult(
    val message: String,
    val completedTaskIds: Set<String> = emptySet()
)

private class HybridChildCoach(private val context: Context) : ChildCoach {
    private val fallback = RuleBasedChildCoach()
    private val gemma = LiteRtGemmaCoach(context, fallback)

    override suspend fun greet(tasks: List<KidTask>): String = gemma.greet(tasks)

    override suspend fun handleChildReply(reply: String, tasks: List<KidTask>): CoachResult {
        val result = fallback.handleChildReply(reply, tasks)
        val aiMessage = gemma.handleChildReply(reply, tasks).message
        return result.copy(message = aiMessage)
    }
}

private class RuleBasedChildCoach : ChildCoach {
    override suspend fun greet(tasks: List<KidTask>): String {
        val remaining = tasks.filterNot { it.completed }
        if (remaining.isEmpty()) {
            return "Hello superstars. Everything is finished for today. Time for a celebration."
        }
        val taskText = remaining.joinToString(", ") { it.title }
        return "Hello team. Today you still have ${remaining.size} task${if (remaining.size == 1) "" else "s"}: $taskText. Which one have you completed?"
    }

    override suspend fun handleChildReply(reply: String, tasks: List<KidTask>): CoachResult {
        val remaining = tasks.filterNot { it.completed }
        if (remaining.isEmpty()) return CoachResult("Everything is already done. Great work.")

        val lower = reply.lowercase(Locale.getDefault())
        val completeAll = listOf("all done", "everything", "finished all", "completed all").any(lower::contains)
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
            completed.isEmpty() -> CoachResult("Thanks for telling me. I did not catch which task is finished yet. Try saying the task name.")
            completed.size == remaining.size -> CoachResult("Amazing. I marked everything as complete.", completed)
            else -> {
                val names = remaining.filter { it.id in completed }.joinToString(", ") { it.title }
                CoachResult("Nice. I marked $names as complete. Keep going.", completed)
            }
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

    override suspend fun greet(tasks: List<KidTask>): String {
        if (!modelFile.exists()) return fallback.greet(tasks)
        return generate(
            "Greet the children warmly. Ask about the unfinished tasks only.\n${taskContext(tasks)}"
        ) ?: fallback.greet(tasks)
    }

    override suspend fun handleChildReply(reply: String, tasks: List<KidTask>): CoachResult {
        if (!modelFile.exists()) return fallback.handleChildReply(reply, tasks)
        val prompt = """
            The child said: "$reply"
            Reply in one short, encouraging spoken sentence.
            Do not claim a task is complete unless the child clearly says it is done.
            ${taskContext(tasks)}
        """.trimIndent()
        val message = generate(prompt) ?: fallback.handleChildReply(reply, tasks).message
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
                    "You are a kind, concise children's task coach. Keep replies safe, upbeat, and under 20 words."
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
    var mode by remember { mutableStateOf(BoardMode.Today) }
    var selectedDay by remember { mutableStateOf(today) }
    var newTask by remember { mutableStateOf("") }
    var coachMessage by remember { mutableStateOf("Tap Hello when everyone is ready.") }
    var listening by remember { mutableStateOf(false) }
    var celebrateCount by remember { mutableIntStateOf(0) }
    val coach = remember { HybridChildCoach(context) }
    val speech = remember { AndroidSpeechOutput(context) }

    DisposableEffect(Unit) {
        onDispose { speech.shutdown() }
    }

    LaunchedEffect(tasks.map { it.id to it.completed }) {
        storage.saveTasks(tasks)
        if (tasks.any { it.day == today } && tasks.filter { it.day == today }.all { it.completed }) {
            celebrateCount++
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) coachMessage = "Microphone permission is needed for the speak button."
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        listening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            scope.launch {
                val todaysTasks = tasks.filter { it.day == today }
                val answer = coach.handleChildReply(heard, todaysTasks)
                if (answer.completedTaskIds.isNotEmpty()) {
                    answer.completedTaskIds.forEach { id ->
                        val index = tasks.indexOfFirst { it.id == id }
                        if (index >= 0) tasks[index] = tasks[index].copy(completed = true)
                    }
                }
                coachMessage = answer.message
                speech.speak(answer.message)
            }
        }
    }

    MaterialTheme(colorScheme = kidColors()) {
        Surface(color = Color(0xFFF8F4EA), modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Header(tasks = tasks.filter { it.day == today })
                    ModeSwitch(mode = mode, onModeChange = { mode = it })
                    DayPicker(selectedDay = selectedDay, onSelected = { selectedDay = it })

                    CoachPanel(
                        message = coachMessage,
                        listening = listening,
                        onHello = {
                            scope.launch {
                                val text = coach.greet(tasks.filter { it.day == today })
                                coachMessage = text
                                speech.speak(text)
                            }
                        },
                        onSpeak = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                listening = true
                                speechLauncher.launch(speechIntent())
                            }
                        }
                    )

                    AddTaskRow(
                        selectedDay = selectedDay,
                        text = newTask,
                        onTextChange = { newTask = it },
                        onAdd = {
                            val clean = newTask.trim()
                            if (clean.isNotBlank()) {
                                tasks.add(KidTask(UUID.randomUUID().toString(), clean, selectedDay))
                                newTask = ""
                            }
                        }
                    )

                    val shownTasks = if (mode == BoardMode.Today) {
                        tasks.filter { it.day == today }
                    } else {
                        tasks.sortedWith(compareBy<KidTask> { it.day.value }.thenBy { it.title })
                    }
                    TaskList(
                        mode = mode,
                        tasks = shownTasks,
                        onToggle = { task ->
                            val index = tasks.indexOfFirst { it.id == task.id }
                            if (index >= 0) tasks[index] = task.copy(completed = !task.completed)
                        },
                        onRemove = { task -> tasks.removeAll { it.id == task.id } }
                    )
                }

                CelebrationOverlay(trigger = celebrateCount)
            }
        }
    }
}

@Composable
private fun Header(tasks: List<KidTask>) {
    val total = tasks.size.coerceAtLeast(1)
    val done = tasks.count { it.completed }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Today Board", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color(0xFF14213D))
            Text("Clean tasks. Kind voice. Big finish.", color = Color(0xFF51606F), fontSize = 15.sp)
        }
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color(0xFF2B6CB0)),
            contentAlignment = Alignment.Center
        ) {
            Text("$done/$total", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
    }
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
private fun CoachPanel(
    message: String,
    listening: Boolean,
    onHello: () -> Unit,
    onSpeak: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFFFFFFF))
            .border(1.dp, Color(0xFFE2DED3), RoundedCornerShape(28.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(message, fontSize = 19.sp, lineHeight = 26.sp, color = Color(0xFF14213D), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onHello, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B6CB0))) {
                Text("Hello", fontWeight = FontWeight.Black)
            }
            OutlinedButton(onClick = onSpeak) {
                Text(if (listening) "Listening..." else "Speak", fontWeight = FontWeight.Black)
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

private class AndroidSpeechOutput(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.UK
            tts.setSpeechRate(0.92f)
            tts.setPitch(1.08f)
        }
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

private class TaskStorage(context: Context) {
    private val preferences = context.getSharedPreferences("kid-day-board", Context.MODE_PRIVATE)

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

private fun DayOfWeek.plusDays(days: Long): DayOfWeek {
    val value = ((this.value - 1 + days) % 7).toInt()
    return DayOfWeek.of(value + 1)
}

private fun speechIntent(): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell the coach what task is complete")
    }
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
