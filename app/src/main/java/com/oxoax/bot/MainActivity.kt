package com.oxoax.bot
import com.oxoax.bot.widgets.BackdropProvider
import com.oxoax.bot.widgets.LocalBackdrop
import com.oxoax.bot.widgets.captureBackdrop

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.oxoax.bot.models.ChatMessage
import com.oxoax.bot.models.Group
import com.oxoax.bot.services.*
import com.oxoax.bot.widgets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalStore.init(this)
        MessageStore.init(this)
        setContent { BackdropProvider { App() } }
    }
    override fun onDestroy() { super.onDestroy(); GlobalWs.dispose() }
}

@Composable
fun App() {
    var isConfigured by remember { mutableStateOf(LocalStore.isConfigured) }
    if (!isConfigured) LoginPage(onDone = { isConfigured = true })
    else MainPage()
}

// ==================== 登录页 ====================

@Composable
fun LoginPage(onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var qq by remember { mutableStateOf("") }
    var botId by remember { mutableStateOf("") }
    var botSecret by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val backdrop = LocalBackdrop.current
    Box(modifier = Modifier.fillMaxSize()) {
        // 背景图
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("https://www.loliapi.com/acg/pe/")
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().captureBackdrop(backdrop),
            contentScale = ContentScale.Crop
        )

        // 毛玻璃遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )

        // 内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            LiquidGlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "XBOT",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "液态玻璃",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // 输入框容器
            LiquidGlassBox(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    GlassInput(name, { name = it }, "机器人名称", "可选")
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassInput(qq, { qq = it }, "机器人QQ号", "可选")
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassInput(botId, { botId = it }, "机器人ID", "必填")
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassInput(botSecret, { botSecret = it }, "机器人密钥", "必填")
                    Spacer(modifier = Modifier.height(20.dp))

                    // 保存按钮
                    LiquidGlassCapsule(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable(enabled = botId.isNotBlank() && botSecret.isNotBlank() && !saving) {
                                saving = true
                                scope.launch {
                                    try {
                                        val avatar = if (qq.isNotEmpty()) "http://q.qlogo.cn/headimg_dl?dst_uin=$qq&spec=640&img_type=jpg" else ""
                                        LocalStore.saveBot(botId.trim(), botSecret.trim(), qq = qq.trim(), name = name.trim(), avatar = avatar)
                                        GlobalWs.init()
                                        onDone()
                                    } catch (_: Exception) {} finally { saving = false }
                                }
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            else Text("保存并开始", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassInput(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (value.isEmpty()) {
                Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                singleLine = true,
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== 首页 ====================

@Composable
fun MainPage() {
    val scope = rememberCoroutineScope()
    var isConnected by remember { mutableStateOf(GlobalWs.isConnected) }
    var groups by remember { mutableStateOf(listOf<Group>()) }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var autoGroups by remember { mutableStateOf(listOf<Map<String, String>>()) }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var inputText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun loadGroups() {
        groups = LocalStore.getGroups().map { Group.fromLocal(it) }
        autoGroups = LocalStore.getAutoGroups()
    }
    fun loadMessages(groupId: String) {
        scope.launch {
            val msgs = withContext(Dispatchers.IO) { MessageStore.loadMessages(groupId) }
            messages = msgs
            if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1)
        }
    }
    fun autoJoinGroup(groupOpenid: String) {
        if (groups.any { it.group == groupOpenid || it.official == groupOpenid }) return
        scope.launch {
            var name = groupOpenid
            try {
                val info = withContext(Dispatchers.IO) { ApiService.fetchGroupInfo(groupOpenid) }
                val n = info?.get("group_name")?.toString()
                if (!n.isNullOrEmpty()) name = n
            } catch (_: Exception) {}
            LocalStore.addGroup(name, groupOpenid, groupOpenid, avatar = LocalStore.botAvatar ?: "")
            LocalStore.addAutoGroup(name, groupOpenid)
            loadGroups()
        }
    }
    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty() || selectedGroup == null) return
        val group = selectedGroup!!
        val targetId = if (group.official.isNotEmpty()) group.official else group.group
        sending = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { ApiService.sendToGroup(targetId, mapOf("msg_type" to 0, "content" to text)) }
                val selfMsg = ChatMessage(time = System.currentTimeMillis().toString(), type = "SELF_SEND", content = text, isSelf = true, username = LocalStore.botName ?: "机器人", userId = LocalStore.botQQ ?: "", sentAt = System.currentTimeMillis())
                MessageStore.appendMessage(group.group, selfMsg)
                inputText = ""
                loadMessages(group.group)
            } catch (e: Exception) { logs = logs + "发送失败: ${e.message}" } finally { sending = false }
        }
    }

    DisposableEffect(Unit) {
        loadGroups()
        val connListener: (Boolean) -> Unit = { c -> isConnected = c; logs = logs + if (c) "✅ 已连接" else "❌ 断开" }
        val eventListener: (JSONObject) -> Unit = { event ->
            val t = event.optString("t", "")
            val d = event.optJSONObject("d")
            if (t == "READY") logs = logs + "✅ 就绪"
            if (d != null && (t == "GROUP_AT_MESSAGE_CREATE" || t == "GROUP_MESSAGE_CREATE" || t == "C2C_MESSAGE_CREATE")) {
                val author = d.optJSONObject("author")
                val groupOpenid = d.optString("group_openid", "")
                val content = d.optString("content", "")
                val nickname = author?.optString("username") ?: author?.optString("member_name") ?: ""
                val userId = author?.optString("member_openid") ?: author?.optString("user_openid") ?: ""
                val msgId = d.optString("id", "")
                val timestamp = d.optString("timestamp", "")
                var clean = content.replace(Regex("<faceType=[^>]*>"), "").replace("<@all>", "@全体成员").replace(Regex("<@!\\d+>"), "").replace("\$", "")
                val msg = ChatMessage(time = timestamp, type = "text", username = nickname, userId = userId, content = clean, messageId = msgId, role = author?.optString("member_role") ?: "member", isBot = author?.optBoolean("bot") ?: false)
                if (t != "C2C_MESSAGE_CREATE" && groupOpenid.isNotEmpty()) {
                    if (groups.none { it.group == groupOpenid || it.official == groupOpenid }) autoJoinGroup(groupOpenid)
                }
                val storeKey = if (t == "C2C_MESSAGE_CREATE") "c2c_$userId" else groupOpenid
                MessageStore.appendMessage(storeKey, msg)
                if (selectedGroup != null && (selectedGroup!!.group == groupOpenid || selectedGroup!!.official == groupOpenid)) loadMessages(selectedGroup!!.group)
                autoGroups = LocalStore.getAutoGroups()
            }
        }
        val logListener: (String) -> Unit = { m -> logs = (logs + m).takeLast(50) }
        GlobalWs.addConnectionListener(connListener)
        GlobalWs.addEventListener(eventListener)
        GlobalWs.addLogListener(logListener)
        GlobalWs.ensureConnected()
        onDispose {
            GlobalWs.removeConnectionListener(connListener)
            GlobalWs.removeEventListener(eventListener)
            GlobalWs.removeLogListener(logListener)
        }
    }

    val backdrop = LocalBackdrop.current
    // 背景
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data("https://www.loliapi.com/acg/pe/").crossfade(true).build(),
            contentDescription = null, modifier = Modifier.fillMaxSize().captureBackdrop(backdrop), contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // 顶部栏 - 液态玻璃
            LiquidGlassBox(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 机器人头像
                    val avatarUrl = LocalStore.botAvatar
                    if (!avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        LiquidGlassCircle(modifier = Modifier.size(40.dp)) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(LocalStore.botName ?: "XBOT", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        Text(
                            "ID: ${LocalStore.botId ?: ""}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { showConfig = true }
                        )
                    }
                    // 连接状态
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
                    )
                }
            }

            // 主内容区
            Row(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                // 左侧群聊列表
                LiquidGlassBox(
                    modifier = Modifier.width(130.dp).fillMaxHeight(),
                    cornerRadius = 16.dp
                ) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            Text("群聊", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        }
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(groups) { group ->
                                val isSelected = selectedGroup?.group == group.group
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedGroup = group; loadMessages(group.group) }
                                        .background(if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(group.name, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (group.group != group.name) Text(group.group, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (autoGroups.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.15f)))
                            Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                Text("自动发现", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                            LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                                items(autoGroups) { ag ->
                                    val n = ag["name"] ?: ""
                                    val oid = ag["group_openid"] ?: ""
                                    Text(n, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth().clickable { LocalStore.addGroup(n, oid, oid); loadGroups() }.padding(horizontal = 12.dp, vertical = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 右侧消息区
                LiquidGlassBox(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    cornerRadius = 16.dp
                ) {
                    if (selectedGroup == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("选择一个群聊", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyColumn(modifier = Modifier.heightIn(max = 200.dp).padding(horizontal = 16.dp)) {
                                    items(logs.takeLast(15)) { l -> Text(l, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 1.dp)) }
                                }
                            }
                        }
                    } else {
                        Column {
                            Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(selectedGroup!!.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                            }
                            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), state = listState) {
                                items(messages) { msg ->
                                    val subColor = Color.White.copy(alpha = 0.4f)
                                    val bg = if (msg.isSelf) Color(0xFF0000FF).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f)
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(msg.username ?: "", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (msg.isSelf) Color(0xFF6B9FFF) else subColor)
                                            Text(msg.time, fontSize = 9.sp, color = subColor.copy(alpha = 0.4f))
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bg).padding(8.dp)) {
                                            MessageContentBuilder.Build(text = msg.content ?: "", isDark = true)
                                        }
                                    }
                                }
                            }
                            // 输入框
                            LiquidGlassBox(modifier = Modifier.fillMaxWidth().padding(8.dp), cornerRadius = 16.dp) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        if (inputText.isEmpty()) Text("输入消息...", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                                        BasicTextField(value = inputText, onValueChange = { inputText = it }, textStyle = TextStyle(color = Color.White, fontSize = 14.sp), singleLine = true, cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    LiquidGlassCircle(modifier = Modifier.size(40.dp).clickable(enabled = inputText.isNotBlank() && !sending) { sendMessage() }) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = if (inputText.isNotBlank()) Color.White else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 底部栏 - 液态玻璃
            LiquidGlassBox(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 群列表图标
                    LiquidGlassCapsule(modifier = Modifier.weight(1f).height(36.dp)) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("群", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        }
                    }
                    // 分割线
                    Box(modifier = Modifier.width(0.5.dp).height(24.dp).background(Color.White.copy(alpha = 0.2f)))
                    // 私列表图标
                    LiquidGlassCapsule(modifier = Modifier.weight(1f).height(36.dp)) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("私", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // 搜索
                    LiquidGlassCircle(modifier = Modifier.size(36.dp)) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // 设置
                    LiquidGlassCircle(modifier = Modifier.size(36.dp).clickable { showConfig = true }) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 配置弹窗
        if (showConfig) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { showConfig = false }) {
                LiquidGlassBox(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp).fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("机器人配置", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("ID: ${LocalStore.botId ?: ""}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        Text("QQ: ${LocalStore.botQQ ?: ""}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        Text("名称: ${LocalStore.botName ?: ""}", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        LiquidGlassCapsule(
                            modifier = Modifier.fillMaxWidth().height(44.dp).clickable { showConfig = false }
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("关闭", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
