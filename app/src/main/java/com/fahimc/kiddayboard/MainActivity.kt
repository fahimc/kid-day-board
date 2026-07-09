package com.fahimc.kiddayboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
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
private enum class VoiceAction { Hello, Speak }

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
        if (remaining.isEmpty()) return CoachResult("Everything is already done, $name. Great work.")

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
            completed.isEmpty() -> CoachResult("Thanks, $name. I did not catch which task is finished yet. Try saying the task name.")
            completed.size == remaining.size -> CoachResult("Amazing, $name. I marked everything as complete.", completed)
            else -> {
                val names = remaining.filter { it.id in completed }.joinToString(", ") { it.title }
                CoachResult("Nice, $name. I marked $names as complete. Keep going.", completed)
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
            Reply in one short, encouraging spoken sentence and use the child's name naturally.
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
    val speech = remember { AndroidSpeechOutput(context) }
    val microphone = remember { InAppMicrophone() }
    val transcriber = remember { LocalWhisperTranscriber(context) }

    DisposableEffect(Unit) {
        onDispose { speech.shutdown() }
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
        Surface(color = Color(0xFFF8F4EA), modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    val todayTasks = tasks.filter { it.day == today }
                    Header(childName = childName, tasks = todayTasks, onSettings = { showSettings = true })

                    ChildTaskList(tasks = todayTasks)

                    VoiceActionButton(
                        action = voiceAction,
                        listening = listening,
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
                            voiceIdleTick++
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                scope.launch {
                                    listening = true
                                    val audio = microphone.recordClip()
                                    val transcription = transcriber.transcribe(audio)
                                    listening = false
                                    if (transcription.text.isNullOrBlank()) {
                                        coachMessage = transcription.userMessage
                                        speech.speak(transcription.userMessage)
                                    } else {
                                        val todaysTasks = tasks.filter { it.day == today }
                                        val answer = coach.handleChildReply(childName, transcription.text, todaysTasks)
                                        if (answer.completedTaskIds.isNotEmpty()) {
                                            answer.completedTaskIds.forEach { id ->
                                                val index = tasks.indexOfFirst { it.id == id }
                                                if (index >= 0) tasks[index] = tasks[index].copy(completed = true)
                                            }
                                        }
                                        coachMessage = answer.message
                                        speech.speak(answer.message)
                                    }
                                    voiceIdleTick++
                                }
                            }
                        }
                    )
                }

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

private class AndroidSpeechOutput(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    var voiceLabel by mutableStateOf("Preparing voice")
        private set
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            refreshVoice()
            tts.setSpeechRate(0.92f)
            tts.setPitch(1.08f)
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
            voiceLabel = "${bestVoice.locale.displayName}: ${bestVoice.name}$network"
        } else {
            tts.language = Locale.UK
            voiceLabel = "Default English voice"
        }
    }

    private fun scoreVoice(voice: Voice): Int {
        val preferredLocale = when (voice.locale.country.uppercase(Locale.US)) {
            "GB" -> 40
            "US" -> 30
            else -> 0
        }
        val networkBoost = if (voice.isNetworkConnectionRequired) 20 else 0
        return voice.quality * 100 - voice.latency * 4 + preferredLocale + networkBoost
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

    @SuppressLint("MissingPermission")
    suspend fun recordClip(maxMillis: Long = 5_000): AudioClip = withContext(Dispatchers.IO) {
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
            recorder.startRecording()
            val deadline = System.currentTimeMillis() + maxMillis
            while (System.currentTimeMillis() < deadline) {
                val count = recorder.read(readBuffer, 0, readBuffer.size)
                if (count > 0) {
                    repeat(count) { index -> samples.add(readBuffer[index]) }
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        AudioClip(samples.toShortArray(), sampleRate)
    }
}

private class LocalWhisperTranscriber(private val context: Context) {
    private val sherpaModelDir: File
        get() = File(context.getExternalFilesDir(null), "models/sherpa-whisper-tiny")
    private val whisperCppModel: File
        get() = File(context.getExternalFilesDir(null), "models/ggml-tiny.en.bin")

    suspend fun transcribe(audioClip: AudioClip): TranscriptionResult = withContext(Dispatchers.IO) {
        if (audioClip.samples.isEmpty()) {
            return@withContext TranscriptionResult(
                text = null,
                userMessage = "I could not hear anything. Try again closer to the microphone."
            )
        }

        if (!sherpaModelDir.exists() && !whisperCppModel.exists()) {
            return@withContext TranscriptionResult(
                text = null,
                userMessage = "I can listen inside the app now, but local Whisper is not installed yet."
            )
        }

        TranscriptionResult(
            text = null,
            userMessage = "The local Whisper model is present, but the native Whisper runtime is not bundled in this APK yet."
        )
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
