package com.qba.dragonaim

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Bundle
import android.os.Process
import android.content.Context
import android.app.ActivityManager
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF0B0910)
private val Panel = Color(0xFF17121F)
private val Purple = Color(0xFFB65CFF)
private val Red = Color(0xFFFF365F)
private val Green = Color(0xFF4CFFB0)
private val Muted = Color(0xFFAAA2B7)
private val featureNames = listOf(
    "Dragon Aim", "Red Phantom", "Shadow Sensi", "Ghost Drag", "Falcon Head",
    "Zero Recoil", "Smooth Lock", "Crimson Aim", "Vortex Sensi", "Phantom X",
    "HeadRush", "AimFlow Pro", "Dark Viper", "Nova Drag", "Titan Sensi"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DragonAimApp(this) }
    }
}

@Composable
private fun DragonAimApp(activity: ComponentActivity) {
    val prefs = remember { activity.getSharedPreferences("dragon_aim", Context.MODE_PRIVATE) }
    var authorized by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var keyMessage by remember { mutableStateOf("") }
    var keyToCheck by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var game by remember { mutableStateOf("ff") }
    var applying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressMessage by remember { mutableStateOf("Sẵn sàng") }
    var cpu by remember { mutableFloatStateOf(0f) }
    var ramMb by remember { mutableIntStateOf(0) }
    var battery by remember { mutableIntStateOf(-1) }
    val enabled = remember {
        mutableStateMapOf<String, Boolean>().apply {
            featureNames.forEach { put(it, prefs.getBoolean("feature_$it", false)) }
        }
    }

    LaunchedEffect(keyToCheck) {
        val candidate = keyToCheck
        if (candidate != null) {
            checking = true
            val result = withContext(Dispatchers.IO) { validateKey(candidate) }
            keyMessage = result.second
            checking = false
            if (result.first) authorized = true
            keyToCheck = null
        }
    }

    LaunchedEffect(Unit) {
        var lastCpu = Process.getElapsedCpuTime()
        var lastWall = System.nanoTime()
        while (true) {
            delay(1500)
            val nowCpu = Process.getElapsedCpuTime()
            val nowWall = System.nanoTime()
            val cpuDelta = (nowCpu - lastCpu).coerceAtLeast(0)
            val wallDeltaMs = ((nowWall - lastWall) / 1_000_000).coerceAtLeast(1)
            cpu = ((cpuDelta.toFloat() / wallDeltaMs) * 100f).coerceIn(0f, 100f)
            lastCpu = nowCpu
            lastWall = nowWall
            val runtime = Runtime.getRuntime()
            ramMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()
            val bm = activity.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple, secondary = Red, background = Bg, surface = Panel,
            onBackground = Color.White, onSurface = Color.White
        )
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF1B0D25), Bg, Color(0xFF100B17)))
            )
        ) {
            if (!authorized) {
                KeyScreen(
                    key = key, onKeyChange = { key = it }, busy = checking,
                    message = keyMessage,
                    onCheck = {
                        if (key.isBlank()) { keyMessage = "Vui lòng nhập key."; return@KeyScreen }
                        keyMessage = "Đang xác minh key..."
                        // API contract is a template; adjust URL/JSON parsing to match your server.
                        keyToCheck = key
                    }
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    Header()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            0 -> AimScreen(enabled, onToggle = { name, value ->
                                enabled[name] = value
                                prefs.edit().putBoolean("feature_$name", value).apply()
                                runCatching {
                                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75).apply {
                                        startTone(if (value) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 100)
                                        // ToneGenerator is short-lived; release after playback.
                                        postDelayedRelease()
                                    }
                                }
                            })
                            1 -> OptimizeScreen(
                                cpu, ramMb, battery, game, { game = it },
                                applying, progress, progressMessage,
                                onApply = {
                                    if (!applying) {
                                        applying = true
                                        progress = 0f
                                        progressMessage = "Đang chuẩn bị..."
                                    }
                                },
                                onLaunch = { launchGame(activity, game) }
                            )
                            else -> AdminScreen()
                        }
                    }
                    Navigation(tab) { tab = it }
                }
                LaunchedEffect(applying) {
                    if (applying) {
                        // Honest local preparation animation; no game injection or memory changes.
                        for (i in 1..30) {
                            delay(1000)
                            progress = i / 30f
                            progressMessage = when {
                                i < 10 -> "Đang kiểm tra thiết lập... $i/30"
                                i < 20 -> "Đang chuẩn bị giao diện... $i/30"
                                i < 30 -> "Đang hoàn tất... $i/30"
                                else -> "Hoàn tất! Có thể mở game."
                            }
                        }
                        applying = false
                    }
                }
            }
        }
    }
}

private fun ToneGenerator.postDelayedRelease() {
    // The tone is brief; release shortly after the tone has started.
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { release() } }, 250)
}

private fun validateKey(key: String): Pair<Boolean, String> {
    return try {
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
        val json = JSONObject().put("key", key)
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(ApiConfig.KEY_CHECK_URL).post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false to "Máy chủ trả lỗi HTTP ${response.code}. Kiểm tra endpoint API."
            val text = response.body?.string().orEmpty()
            val obj = JSONObject(text)
            val valid = obj.optBoolean("valid", obj.optBoolean("success", false))
            if (valid) true to "Key hợp lệ." else false to (obj.optString("message").ifBlank { "Key không hợp lệ." })
        }
    } catch (e: Exception) {
        false to "Không kết nối được API. Kiểm tra URL và định dạng phản hồi."
    }
}

@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).background(Brush.linearGradient(listOf(Red, Purple)), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) { Text("QB", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("QB DRAGON AIM", fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("QbaoTweakxVft King", fontSize = 11.sp, color = Purple)
        }
        Box(Modifier.size(9.dp).background(Green, CircleShape))
    }
}

@Composable
private fun KeyScreen(
    key: String, onKeyChange: (String) -> Unit, busy: Boolean, message: String, onCheck: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(94.dp).background(Brush.linearGradient(listOf(Red, Purple)), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) { Text("QB", fontSize = 38.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(22.dp))
        Text("QB DRAGON AIM", fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("SECURE KEY VERIFICATION", color = Purple, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(28.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nhập KEY truy cập", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = key, onValueChange = onKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("License key") },
                    placeholder = { Text("Nhập key của bạn") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onCheck, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("XÁC MINH KEY", fontWeight = FontWeight.Bold)
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = if (message.contains("hợp lệ")) Green else Red, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Admin: PHẠM GIA BẢO", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun AimScreen(enabled: Map<String, Boolean>, onToggle: (String, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("HỖ TRỢ KÉO TÂM", fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text("Bật/tắt tùy chọn giao diện · Không can thiệp game", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            itemsIndexed(featureNames) { _, name ->
                Row(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(15.dp))
                        .border(1.dp, if (enabled[name] == true) Purple.copy(alpha = .65f) else Color.White.copy(alpha = .05f), RoundedCornerShape(15.dp))
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(36.dp).background(if (enabled[name] == true) Red.copy(alpha = .18f) else Purple.copy(alpha = .12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Text("◎", color = if (enabled[name] == true) Red else Purple, fontSize = 21.sp)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(if (enabled[name] == true) "ĐANG BẬT" else "ĐANG TẮT", color = if (enabled[name] == true) Green else Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                    Switch(checked = enabled[name] == true, onCheckedChange = { onToggle(name, it) })
                }
            }
        }
    }
}

@Composable
private fun OptimizeScreen(
    cpu: Float, ramMb: Int, battery: Int, game: String, onGame: (String) -> Unit,
    applying: Boolean, progress: Float, progressMessage: String, onApply: () -> Unit, onLaunch: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("TỐI ƯU THIẾT BỊ", fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text("Chỉ số tiến trình của ứng dụng QB Dragon Aim", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("CPU ứng dụng", "${cpu.toInt()}%", Modifier.weight(1f))
            MetricCard("RAM ứng dụng", "$ramMb MB", Modifier.weight(1f))
            MetricCard("PIN", if (battery >= 0) "$battery%" else "N/A", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Text("CPU LIVE GRAPH", fontWeight = FontWeight.Bold)
                Text("Ước tính CPU của tiến trình app · cập nhật 1,5 giây", color = Muted, fontSize = 10.sp)
                Spacer(Modifier.height(10.dp))
                CpuGraph(cpu)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("CHỌN PHIÊN BẢN GAME", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameChoice("Free Fire thường", game == "ff", Modifier.weight(1f)) { onGame("ff") }
            GameChoice("Free Fire MAX", game == "ffm", Modifier.weight(1f)) { onGame("ffm") }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onApply, enabled = !applying, modifier = Modifier.fillMaxWidth()) {
            Text(if (applying) "ĐANG CHUẨN BỊ..." else "ÁP DỤNG TỐI ƯU")
        }
        if (applying || progress >= 1f) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Green)
            Text(progressMessage, color = Green, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (!applying && progress >= 1f) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLaunch, modifier = Modifier.fillMaxWidth()) {
                Text(if (game == "ff") "MỞ FREE FIRE" else "MỞ FREE FIRE MAX")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Tiến trình chỉ chuẩn bị thiết lập trong app; không chèn mã hay sửa bộ nhớ game.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(14.dp)).padding(10.dp)) {
        Text(title, color = Muted, fontSize = 9.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = Purple, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CpuGraph(cpu: Float) {
    var samples by remember { mutableStateOf(List(24) { 0.0f }) }
    LaunchedEffect(cpu) { samples = (samples.drop(1) + cpu / 100f) }
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val stepY = size.height / 4
        for (i in 1..3) drawLine(Color.White.copy(alpha = .10f), androidx.compose.ui.geometry.Offset(0f, stepY * i), androidx.compose.ui.geometry.Offset(size.width, stepY * i), 1.dp.toPx())
        val path = Path()
        samples.forEachIndexed { i, v ->
            val x = size.width * i / (samples.size - 1)
            val y = size.height * (1f - v.coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Purple, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
    }
}

@Composable
private fun GameChoice(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.background(if (selected) Purple.copy(alpha = .18f) else Panel, RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) Purple else Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (selected) "●" else "○", color = if (selected) Green else Muted, fontSize = 18.sp)
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AdminScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(92.dp).background(Brush.linearGradient(listOf(Red, Purple)), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            Text("QB", fontSize = 36.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(20.dp))
        Text("QbaoTweakxVft King", fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("PHẠM GIA BẢO", color = Purple, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("QB Dragon Aim", fontWeight = FontWeight.Bold)
                Text("Phiên bản 1.0 · Android", color = Muted, fontSize = 12.sp)
                Text("Thiết kế đỏ–tím · Key API · Hiệu suất", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Navigation(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF100D16)).padding(horizontal = 8.dp, vertical = 8.dp)) {
        listOf("Kéo tâm", "Tối ưu máy", "Admin").forEachIndexed { index, label ->
            Column(
                Modifier.weight(1f).clickable { onSelect(index) }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(listOf("◎", "▥", "♛")[index], color = if (selected == index) Purple else Muted, fontSize = 21.sp)
                Text(label, color = if (selected == index) Color.White else Muted, fontSize = 10.sp, fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

private fun launchGame(activity: ComponentActivity, game: String) {
    val packageName = if (game == "ff") "com.dts.freefireth" else "com.dts.freefiremax"
    val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        activity.startActivity(intent)
    } else {
        android.widget.Toast.makeText(activity, "Chưa tìm thấy game đã chọn trên thiết bị.", android.widget.Toast.LENGTH_LONG).show()
    }
}
