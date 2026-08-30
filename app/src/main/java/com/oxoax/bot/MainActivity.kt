package com.oxoax.bot

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

// ==================== 群聊数据 ====================

data class GroupChatState(
    val group: Group,
    val lastMessage: ChatMessage? = null,
    val unreadCount: Int = 0,
    val lastActiveTime: Long = 0L
)

// ==================== App ====================

@Composable
fun App() {
    val isConfigured = remember { mutableStateOf(LocalStore.isConfigured) }

    if (!isConfigured.value) {
        MainPage(
            showConfigInitially = true,
            onConfigDone = { isConfigured.value = true }
        )
    } else {
        GlobalWs.init()
        MainPage(onConfigDone = {})
    }
}

// ==================== 主页面 ====================

@Composable
fun MainPage(
    showConfigInitially: Boolean = false,
    onConfigDone: () -> Unit = {}
) {
    var showConfig by remember { mutableStateOf(showConfigInitially) }
    var selectedTab by remember { mutableStateOf(0) } // 0=群聊 1=私聊
    var wsConnected by remember { mutableStateOf(GlobalWs.isConnected) }
    val groupStates = remember { mutableStateListOf<GroupChatState>() }
    val scope = rememberCoroutineScope()

    // 监听 WebSocket 连接状态
    DisposableEffect(Unit) {
        val connListener: (Boolean) -> Unit = { wsConnected = it }
        GlobalWs.addConnectionListener(connListener)
        // 确保连接
        GlobalWs.ensureConnected()
        onDispose { GlobalWs.removeConnectionListener(connListener) }
    }

    // 加载群列表
    LaunchedEffect(Unit) {
        val groups = LocalStore.getGroups().map { Group.fromLocal(it) }
        groupStates.clear()
        groupStates.addAll(groups.map { g ->
            val msgs = MessageStore.loadMessages(g.group)
            GroupChatState(
                group = g,
                lastMessage = msgs.lastOrNull(),
                unreadCount = 0,
                lastActiveTime = LocalStore.getGroupLastActive()[g.group] ?: 0L
            )
        })
        groupStates.sortByDescending { it.lastActiveTime }
    }

    // 监听新消息
    DisposableEffect(Unit) {
        val eventListener: (JSONObject) -> Unit = { event ->
            scope.launch {
                try {
                    val data = event.optJSONObject("d") ?: return@launch
                    val groupId = data.optString("group_openid", "")
                    if (groupId.isNotEmpty()) {
                        val msg = ChatMessage(
                            time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                            type = data.optString("content_type", "text"),
                            username = data.optJSONObject("author")?.optString("username", ""),
                            userId = data.optJSONObject("author")?.optString("id", ""),
                            content = data.optString("content", ""),
                            isAt = data.optJSONArray("mentions")?.let { arr ->
                                (0 until arr.length()).any { arr.getJSONObject(it).optString("id") == LocalStore.botQQ }
                            } ?: false
                        )
                        MessageStore.appendMessage(groupId, msg)
                        LocalStore.setGroupLastActive(groupId, System.currentTimeMillis())

                        val idx = groupStates.indexOfFirst { it.group.group == groupId }
                        if (idx >= 0) {
                            val old = groupStates[idx]
                            groupStates[idx] = old.copy(
                                lastMessage = msg,
                                unreadCount = old.unreadCount + 1,
                                lastActiveTime = System.currentTimeMillis()
                            )
                            // 移到最上面
                            val item = groupStates.removeAt(idx)
                            groupStates.add(0, item)
                        } else {
                            // 新群自动加入
                            val name = data.optString("group_name", "群聊$groupId")
                            LocalStore.addAutoGroup(name, groupId)
                            val newGroup = Group(name = name, group = groupId)
                            groupStates.add(0, GroupChatState(
                                group = newGroup,
                                lastMessage = msg,
                                unreadCount = 1,
                                lastActiveTime = System.currentTimeMillis()
                            ))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainPage", "处理消息失败", e)
                }
            }
        }
        GlobalWs.addEventListener(eventListener)
        onDispose { GlobalWs.removeEventListener(eventListener) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF2F2F7))) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {

            // ===== 顶栏 =====
            TopBar(
                wsConnected = wsConnected,
                onSettingsClick = { showConfig = true }
            )

            // ===== 群聊/私聊列表 =====
            Box(modifier = Modifier.weight(1f)) {
                if (groupStates.isEmpty()) {
                    // 空状态 - 中间显示配置按钮
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LiquidGlassCapsule(
                            modifier = Modifier
                                .height(48.dp)
                                .clickable { showConfig = true }
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                                Text("配置机器人", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                        }
                    }
                } else {
                    GroupList(groupStates = groupStates)
                }
            }

            // ===== 底栏 =====
            BottomBar(
                selectedTab = selectedTab,
                onTabSelect = { selectedTab = it },
                onSearch = {}
            )
        }

        // ===== 配置弹窗 =====
        if (showConfig) {
            ConfigBottomSheet(
                onDismiss = {
                    showConfig = false
                    if (LocalStore.isConfigured) onConfigDone()
                }
            )
        }
    }
}

// ==================== 顶栏 ====================

@Composable
fun TopBar(
    wsConnected: Boolean,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左边 - 头像+名称玻璃容器
        LiquidGlassCapsule(
            modifier = Modifier.weight(1f).height(52.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像
                val avatarUrl = LocalStore.botAvatar
                if (!avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🤖", fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            LocalStore.botName ?: "未配置",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // WebSocket 状态
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (wsConnected) Color(0xFF34C759) else Color(0xFFFF3B30))
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            if (wsConnected) "在线中" else "已离线",
                            color = if (wsConnected) Color(0xFF34C759) else Color(0xFFFF3B30),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        "ID: ${LocalStore.botId ?: "---"}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 右边 - 设置玻璃容器
        LiquidGlassCapsule(
            modifier = Modifier.size(52.dp).clickable { onSettingsClick() }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ==================== 群聊列表 ====================

@Composable
fun GroupList(groupStates: List<GroupChatState>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(groupStates, key = { it.group.group }) { state ->
            GroupCard(state = state)
            if (groupStates.lastOrNull() != state) {
                // 细线分割
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(0.5.dp)
                        .background(Color.Black.copy(alpha = 0.08f))
                )
            }
        }
    }
}

@Composable
fun GroupCard(state: GroupChatState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 群头像
        val avatarUrl = state.group.avatarUrl
        if (avatarUrl.isNotEmpty()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE5E5EA)),
                contentAlignment = Alignment.Center
            ) {
                Text(state.group.name.take(1), fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 名称 + 最后消息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                state.group.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val lastMsg = state.lastMessage
            if (lastMsg != null) {
                Text(
                    "${lastMsg.username ?: ""}: ${lastMsg.content ?: ""}",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 时间 + 未读数
        Column(horizontalAlignment = Alignment.End) {
            Text(
                state.lastMessage?.time ?: "",
                fontSize = 11.sp,
                color = Color.Gray
            )
            if (state.unreadCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.unreadCount > 99) "99+" else state.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==================== 底栏 ====================

@Composable
fun BottomBar(
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 群聊/私聊切换
        LiquidGlassCapsule(
            modifier = Modifier.weight(1f).height(44.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 群聊
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelect(0) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "群聊",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                // 细线分割
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(24.dp)
                        .align(Alignment.CenterVertically)
                        .background(Color.White.copy(alpha = 0.3f))
                )
                // 私聊
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelect(1) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "私聊",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 静音/搜索
        LiquidGlassCapsule(
            modifier = Modifier.height(44.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 4.dp).fillMaxHeight()) {
                Box(
                    modifier = Modifier.size(44.dp).clickable { },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔇", fontSize = 18.sp)
                }
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(24.dp)
                        .align(Alignment.CenterVertically)
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Box(
                    modifier = Modifier.size(44.dp).clickable { onSearch() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Search, contentDescription = "搜索", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ==================== 配置弹窗 ====================

@Composable
fun ConfigBottomSheet(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(LocalStore.botName ?: "") }
    var qq by remember { mutableStateOf(LocalStore.botQQ ?: "") }
    var botId by remember { mutableStateOf(LocalStore.botId ?: "") }
    var botSecret by remember { mutableStateOf(LocalStore.botSecret ?: "") }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable { onDismiss() }
    ) {
        LiquidGlassBox(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clickable { } // 防止穿透
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("配置机器人", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)

                ConfigInput("名称", name) { name = it }
                ConfigInput("QQ号", qq) { qq = it }
                ConfigInput("AppID", botId) { botId = it }
                ConfigInput("Secret", botSecret) { botSecret = it }

                LiquidGlassCapsule(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable {
                            if (botId.isNotBlank() && botSecret.isNotBlank()) {
                                scope.launch {
                                    LocalStore.saveBot(botId, botSecret, qq, name)
                                    GlobalWs.reconnect()
                                    withContext(Dispatchers.Main) { onDismiss() }
                                }
                            }
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("保存", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        LiquidGlassCard(cornerRadius = 12.dp) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                singleLine = true,
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                    inner()
                }
            )
        }
    }
}
