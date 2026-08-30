package com.oxoax.bot

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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

data class GroupChatState(
    val group: Group,
    val lastMessage: ChatMessage? = null,
    val unreadCount: Int = 0,
    val lastActiveTime: Long = 0L
)

@Composable
fun App() {
    val isConfigured = remember { mutableStateOf(LocalStore.isConfigured) }
    if (!isConfigured.value) {
        MainPage(showConfigInitially = true, onConfigDone = { isConfigured.value = true })
    } else {
        GlobalWs.init()
        MainPage(onConfigDone = {})
    }
}

@Composable
fun MainPage(showConfigInitially: Boolean = false, onConfigDone: () -> Unit = {}) {
    var showConfig by remember { mutableStateOf(showConfigInitially) }
    var selectedTab by remember { mutableStateOf(0) }
    var wsConnected by remember { mutableStateOf(GlobalWs.isConnected) }
    val groupStates = remember { mutableStateListOf<GroupChatState>() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = { wsConnected = it }
        GlobalWs.addConnectionListener(listener)
        GlobalWs.ensureConnected()
        onDispose { GlobalWs.removeConnectionListener(listener) }
    }

    LaunchedEffect(Unit) {
        val groups = LocalStore.getGroups().map { Group.fromLocal(it) }
        groupStates.clear()
        groupStates.addAll(groups.map { g ->
            val msgs = MessageStore.loadMessages(g.group)
            GroupChatState(g, msgs.lastOrNull(), 0, LocalStore.getGroupLastActive()[g.group] ?: 0L)
        })
        groupStates.sortByDescending { it.lastActiveTime }
    }

    DisposableEffect(Unit) {
        val listener: (JSONObject) -> Unit = { event ->
            scope.launch {
                try {
                    val d = event.optJSONObject("d") ?: return@launch
                    val gid = d.optString("group_openid", "")
                    if (gid.isNotEmpty()) {
                        val msg = ChatMessage(
                            time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                            type = d.optString("content_type", "text"),
                            username = d.optJSONObject("author")?.optString("username", ""),
                            userId = d.optJSONObject("author")?.optString("id", ""),
                            content = d.optString("content", "")
                        )
                        MessageStore.appendMessage(gid, msg)
                        LocalStore.setGroupLastActive(gid, System.currentTimeMillis())
                        val idx = groupStates.indexOfFirst { it.group.group == gid }
                        if (idx >= 0) {
                            val old = groupStates[idx]
                            groupStates[idx] = old.copy(lastMessage = msg, unreadCount = old.unreadCount + 1, lastActiveTime = System.currentTimeMillis())
                            val item = groupStates.removeAt(idx)
                            groupStates.add(0, item)
                        } else {
                            val name = d.optString("group_name", "群聊")
                            LocalStore.addAutoGroup(name, gid)
                            groupStates.add(0, GroupChatState(Group(name = name, group = gid), msg, 1, System.currentTimeMillis()))
                        }
                    }
                } catch (e: Exception) { Log.e("MainPage", "err", e) }
            }
        }
        GlobalWs.addEventListener(listener)
        onDispose { GlobalWs.removeEventListener(listener) }
    }

    // 渐变背景
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1a1a2e),
                    Color(0xFF16213e),
                    Color(0xFF0f3460)
                )
            )
        )
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // 顶栏
            TopBar(wsConnected, onSettingsClick = { showConfig = true })

            Spacer(modifier = Modifier.height(8.dp))

            // 列表
            Box(modifier = Modifier.weight(1f)) {
                if (groupStates.isEmpty()) {
                    EmptyState(onConfig = { showConfig = true })
                } else {
                    GroupList(groupStates)
                }
            }

            // 底栏
            BottomBar(selectedTab, onTabSelect = { selectedTab = it })
        }

        if (showConfig) {
            ConfigBottomSheet(onDismiss = {
                showConfig = false
                if (LocalStore.isConfigured) onConfigDone()
            })
        }
    }
}

@Composable
fun TopBar(wsConnected: Boolean, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左边 - 头像信息
        LiquidGlassCapsule(modifier = Modifier.weight(1f).height(56.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像
                val avatarUrl = LocalStore.botAvatar
                if (!avatarUrl.isNullOrEmpty()) {
                    AsyncImage(model = avatarUrl, contentDescription = null,
                        modifier = Modifier.size(38.dp).clip(CircleShape))
                } else {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Text("🤖", fontSize = 20.sp) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(LocalStore.botName ?: "未配置",
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.width(8.dp))
                        // 状态点
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape)
                            .background(if (wsConnected) Color(0xFF34C759) else Color(0xFFFF453A)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (wsConnected) "在线" else "离线",
                            color = if (wsConnected) Color(0xFF34C759) else Color(0xFFFF453A),
                            fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Text("ID: ${LocalStore.botId ?: "---"}",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 右边 - 设置
        LiquidGlassCapsule(modifier = Modifier.size(56.dp).clickable { onSettingsClick() }) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Settings, "设置", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun EmptyState(onConfig: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🤖", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("还没有配置机器人", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))
            LiquidGlassCapsule(modifier = Modifier.height(48.dp).clickable { onConfig() }) {
                Box(modifier = Modifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                    Text("配置机器人", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun GroupList(groupStates: List<GroupChatState>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(groupStates, key = { it.group.group }) { state ->
            GroupCard(state)
            if (groupStates.lastOrNull() != state) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.06f)))
            }
        }
    }
}

@Composable
fun GroupCard(state: GroupChatState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        val url = state.group.avatarUrl
        if (url.isNotEmpty()) {
            AsyncImage(model = url, contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape))
        } else {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(state.group.name.take(1), fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // 名称 + 消息
        Column(modifier = Modifier.weight(1f)) {
            Text(state.group.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            val msg = state.lastMessage
            if (msg != null) {
                Text("${msg.username ?: ""}: ${msg.content ?: ""}",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // 时间 + 未读
        Column(horizontalAlignment = Alignment.End) {
            Text(state.lastMessage?.time ?: "", fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f))
            if (state.unreadCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center) {
                    Text(if (state.unreadCount > 99) "99+" else state.unreadCount.toString(),
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BottomBar(selectedTab: Int, onTabSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 群聊/私聊
        LiquidGlassCapsule(modifier = Modifier.weight(1f).height(48.dp)) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { onTabSelect(0) },
                    contentAlignment = Alignment.Center) {
                    Text("群聊", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White, fontSize = 15.sp)
                }
                Box(modifier = Modifier.width(0.5.dp).height(26.dp).align(Alignment.CenterVertically)
                    .background(Color.White.copy(alpha = 0.2f)))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { onTabSelect(1) },
                    contentAlignment = Alignment.Center) {
                    Text("私聊", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White, fontSize = 15.sp)
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 静音/搜索
        LiquidGlassCapsule(modifier = Modifier.height(48.dp)) {
            Row(modifier = Modifier.padding(horizontal = 4.dp).fillMaxHeight()) {
                Box(modifier = Modifier.size(48.dp).clickable { }, contentAlignment = Alignment.Center) {
                    Text("🔇", fontSize = 20.sp)
                }
                Box(modifier = Modifier.width(0.5.dp).height(26.dp).align(Alignment.CenterVertically)
                    .background(Color.White.copy(alpha = 0.2f)))
                Box(modifier = Modifier.size(48.dp).clickable { }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Search, "搜索", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
fun ConfigBottomSheet(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(LocalStore.botName ?: "") }
    var qq by remember { mutableStateOf(LocalStore.botQQ ?: "") }
    var botId by remember { mutableStateOf(LocalStore.botId ?: "") }
    var botSecret by remember { mutableStateOf(LocalStore.botSecret ?: "") }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { onDismiss() }) {
        LiquidGlassBox(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable { }) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("配置机器人", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(2.dp))
                ConfigField("名称", name) { name = it }
                ConfigField("QQ号", qq) { qq = it }
                ConfigField("AppID", botId) { botId = it }
                ConfigField("Secret", botSecret) { botSecret = it }
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassCapsule(modifier = Modifier.fillMaxWidth().height(50.dp).clickable {
                    if (botId.isNotBlank() && botSecret.isNotBlank()) {
                        scope.launch {
                            LocalStore.saveBot(botId, botSecret, qq, name)
                            GlobalWs.reconnect()
                            withContext(Dispatchers.Main) { onDismiss() }
                        }
                    }
                }) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("保存", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigField(label: String, value: String, onChange: (String) -> Unit) {
    LiquidGlassCard(cornerRadius = 14.dp) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            BasicTextField(value = value, onValueChange = onChange,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                singleLine = true, cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                decorationBox = { if (value.isEmpty()) Text("请输入$label", color = Color.White.copy(alpha = 0.3f), fontSize = 15.sp); it() })
        }
    }
}
