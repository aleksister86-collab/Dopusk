package com.example.dopusk2

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { DopuskScreen() } }
    }
}

// ---------- данные ----------

data class Question(val disc: String, val num: String, val text: String, val answers: List<String>) {
    val key = norm(num)
}

fun norm(s: String) = s.lowercase().replace(" ", "")

fun loadQuestions(ctx: android.content.Context): List<Question> {
    val raw = ctx.assets.open("questions.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(raw)
    return List(arr.length()) { i ->
        val r = arr.getJSONArray(i)
        Question(
            r.getString(0), r.getString(1), r.getString(2),
            r.getString(3).split(" | ").map { it.trim() }.filter { it.isNotEmpty() }
        )
    }
}

/** Точное совпадение номера + вложенные («1.4» найдёт 1.4.(1), 1.4.(2)…), иначе — «содержит». */
fun search(all: List<Question>, query: String): List<Question> {
    val k = norm(query).trim()
    if (k.isEmpty()) return emptyList()
    val main = all.filter {
        it.key == k || (it.key.startsWith(k) && !it.key[k.length].isDigit())
    }
    return (if (main.isNotEmpty()) main else all.filter { it.key.contains(k) }).take(300)
}

// ---------- тема ----------

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF9DB8FF))
        else -> lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF1E5BD8))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------- экран ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DopuskScreen() {
    val ctx = LocalContext.current
    var all by remember { mutableStateOf<List<Question>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Question>?>(null) }
    var disc by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { loadQuestions(ctx) } }

    fun doSearch() { results = search(all, input); disc = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Допуск — ответы", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    QueryField(input, enabled = all.isNotEmpty())
                    Spacer(Modifier.height(12.dp))
                    Keyboard(
                        onKey = { input += it },
                        onBackspace = { input = input.dropLast(1) },
                        onClear = { input = ""; results = null },
                        onSearch = ::doSearch
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val res = results
            when {
                all.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                res == null -> Hint("Введите номер вопроса\nнапример: 1.4.(1)", "База: ${all.size} вопросов")
                res.isEmpty() -> Hint("Ничего не найдено", "Проверьте номер вопроса")
                else -> {
                    val discs = res.map { it.disc }.distinct()
                    val shown = if (disc == null) res else res.filter { it.disc == disc }
                    Column {
                        if (discs.size > 1) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(disc == null, { disc = null }, label = { Text("Все (${res.size})") })
                                }
                                items(discs) { d ->
                                    FilterChip(disc == d, { disc = d }, label = { Text(d) })
                                }
                            }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) { items(shown) { QuestionCard(it) } }
                    }
                }
            }
        }
    }
}

@Composable
fun Hint(title: String, sub: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .6f))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun QueryField(value: String, enabled: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            if (value.isEmpty()) {
                Text(
                    if (enabled) "Номер вопроса (например: 2.1.1)" else "Загрузка базы…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
fun QuestionCard(q: Question) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    q.disc, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("№ ${q.num}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            if (q.text.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Image, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Вопрос в виде рисунка/схемы (по номеру)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(q.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            if (q.answers.isEmpty()) {
                Text("Ответ не указан", color = MaterialTheme.colorScheme.error)
            }
            q.answers.forEach { a ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = androidx.compose.ui.graphics.Color(0xFF2E9E5B))
                    Spacer(Modifier.width(8.dp))
                    Text(a, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ---------- клавиатура ----------

@Composable
fun Keyboard(onKey: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit, onSearch: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    fun tap(action: () -> Unit) = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); action() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("1", onClick = tap { onKey("1") }); Key("2", onClick = tap { onKey("2") }); Key("3", onClick = tap { onKey("3") })
            Key(icon = true, onClick = tap(onBackspace), onLongClick = onClear)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("4", onClick = tap { onKey("4") }); Key("5", onClick = tap { onKey("5") }); Key("6", onClick = tap { onKey("6") })
            Key("(", onClick = tap { onKey("(") }, tonal = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("7", onClick = tap { onKey("7") }); Key("8", onClick = tap { onKey("8") }); Key("9", onClick = tap { onKey("9") })
            Key(")", onClick = tap { onKey(")") }, tonal = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(".", onClick = tap { onKey(".") }, tonal = true); Key("0", onClick = tap { onKey("0") }); Key("п", onClick = tap { onKey("п") }, tonal = true)
            Key("НАЙТИ", onClick = tap(onSearch), primary = true, small = true)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.Key(
    label: String = "",
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: Boolean = false,
    tonal: Boolean = false,
    primary: Boolean = false,
    small: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val bg = when {
        primary -> cs.primary
        icon -> cs.secondaryContainer
        tonal -> cs.surfaceContainerHigh
        else -> cs.surfaceContainerLowest
    }
    val fg = when {
        primary -> cs.onPrimary
        icon -> cs.onSecondaryContainer
        else -> cs.onSurface
    }
    Box(
        Modifier.weight(1f).height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon) Icon(Icons.AutoMirrored.Rounded.Backspace, "Стереть", tint = fg)
        else Text(label, color = fg, fontSize = if (small) 13.sp else 24.sp, fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal)
    }
}
