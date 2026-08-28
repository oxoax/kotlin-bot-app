package com.oxoax.bot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxoax.bot.models.ChatMessage
import com.oxoax.bot.models.Group
import com.oxoax.bot.services.*
import com.oxoax.bot.widgets.MessageContentBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LocalStore.init(this)
        MessageStore.init(this)

        setContent {
            val isDark = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDark) Color(0xFF0A0A0F) else Color(0xFFF2F2F7)
                ) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App() {
    var isConfigured by remember { mutableStateOf(LocalStore.isConfigured) }

    if (!isConfigured) {
        SetupPage(onDone = { isConfigured = true })
    } else {
        HomePage()
    }
}

// ==================== 设置页 ====================

@Composable
fun SetupPage(onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var qq by remember { mutableStateOf("") }
    var botId by remember { mutableStateOf("") }
    var botSecret by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    val txtColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.9f)
    val subColor = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)
    val bgColor = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = txtColor
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("机器人配置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = txtColor)
        Spacer(modifier = Modifier.height(28.dp))

        // 机器人名称
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("机器人名称") },
            placeholder = { Text("可选", color = subColor) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(14.dp))

        // QQ号
        OutlinedTextField(
            value = qq,
            onValueChange = { qq = it },
            label = { Text("机器人QQ号") },
            placeholder = { Text("可选", color = subColor) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(14.dp))

        // App ID
        OutlinedTextField(
            value = botId,
            onValueChange = { botId = it },
            label = { Text("机器人ID *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(14.dp))

        // App Secret
        OutlinedTextField(
            value = botSecret,
            onValueChange = { botSecret = it },
            label = { Text("机器人密钥 *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (botId.isBlank() || botSecret.isBlank()) return@Button
                saving = true
                scope.launch {
                    try {
                        val avatar = if (qq.isNotEmpty()) "http://q.qlogo.cn/headimg_dl?dst_uin=$qq&spec=640&img_type=jpg" else ""
                        LocalStore.saveBot(botId.trim(), botSecret.trim(), qq = qq.trim(), name = name.trim(), avatar = avatar)
                        GlobalWs.init()
                        onDone()
                    } catch (_: Exception) {
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = botId.isNotBlank() && botSecret.isNotBlank() && !saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("保存并开始", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ==================== 首页 ====================

@Composable
fun HomePage() {
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    val txtColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.9f)
    val subColor = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)
    val bgColor = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)

    var isConnected by remember { mutableStateOf(GlobalWs.isConnected) }
    var groups by remember { mutableStateOf(listOf<Group>()) }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var autoGroups by remember { mutableStateOf(listOf<Map<String, String>>()) }

    // 加载本地群聊
    fun loadGroups() {
        groups = LocalStore.getGroups().map { Group.fromLocal(it) }
        autoGroups = LocalStore.getAutoGroups()
    }

    // 加载消息
    fun loadMessages(groupId: String) {
        scope.launch {
            val msgs = withContext(Dispatchers.IO) { MessageStore.loadMessages(groupId) }
            messages = msgs
        }
    }

    // 自动加入群聊
    fun autoJoinGroup(groupOpenid: String) {
        if (groups.any { it.group == groupOpenid || it.official == groupOpenid }) return
        scope.launch {
            var name = groupOpenid
            try {
                val info = withContext(Dispatchers.IO) { ApiService.fetchGroupInfo(groupOpenid) }
                if (info != null) {
                    val groupName = info["group_name"]?.toString()
                    if (!groupName.isNullOrEmpty()) name = groupName
                }
            } catch (_: Exception) {}
            val botAvatar = LocalStore.botAvatar ?: ""
            LocalStore.addGroup(name, groupOpenid, groupOpenid, avatar = botAvatar)
            LocalStore.addAutoGroup(name, groupOpenid)
            loadGroups()
        }
    }

    // 初始化
    DisposableEffect(Unit) {
        loadGroups()

        val connListener: (Boolean) -> Unit = { connected ->
            isConnected = connected
        }
        val eventListener: (JSONObject) -> Unit = { event ->
            val t = event.optString("t", "")
            val d = event.optJSONObject("d")
            if (d != null && (t == "GROUP_AT_MESSAGE_CREATE" || t == "GROUP_MESSAGE_CREATE" || t == "C2C_MESSAGE_CREATE")) {
                val author = d.optJSONObject("author")
                val groupOpenid = d.optString("group_openid", "")
                val content = d.optString("content", "")
                val nickname = author?.optString("username") ?: ""
                val userId = author?.optString("member_openid") ?: author?.optString("user_openid") ?: ""
                val msgId = d.optString("id", "")
                val timestamp = d.optString("timestamp", "")

                val msg = ChatMessage(
                    time = timestamp,
                    type = "text",
                    username = nickname,
                    userId = userId,
                    content = content,
                    messageId = msgId,
                    role = author?.optString("member_role") ?: "member",
                    isBot = author?.optBoolean("bot") ?: false,
                    rawPayload = d.keys().asSequence().associateWith { d.get(it) }
                )

                // 自动加入未知群
                if (t != "C2C_MESSAGE_CREATE" && groupOpenid.isNotEmpty()) {
                    val matchGroup = groups.find { it.group == groupOpenid || it.official == groupOpenid }
                    if (matchGroup == null) {
                        autoJoinGroup(groupOpenid)
                    }
                }

                // 保存消息
                val storeKey = if (t == "C2C_MESSAGE_CREATE") "c2c_$userId" else groupOpenid
                MessageStore.appendMessage(storeKey, msg)

                // 如果是当前选中的群，刷新消息
                if (selectedGroup != null && (selectedGroup!!.group == groupOpenid || selectedGroup!!.official == groupOpenid)) {
                    loadMessages(selectedGroup!!.group)
                }

                // 更新自动发现列表
                autoGroups = LocalStore.getAutoGroups()
            }
        }

        GlobalWs.addConnectionListener(connListener)
        GlobalWs.addEventListener(eventListener)
        GlobalWs.ensureConnected()

        onDispose {
            GlobalWs.removeConnectionListener(connListener)
            GlobalWs.removeEventListener(eventListener)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部状态栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isConnected) "已连接" else "未连接",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(modifier = Modifier.weight(1f)) {
            // 左侧：群聊列表
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .background(bgColor)
            ) {
                // 群聊标题
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("群聊", fontWeight = FontWeight.Bold, color = txtColor, fontSize = 14.sp)
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(groups) { group ->
                        val isSelected = selectedGroup?.group == group.group
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGroup = group
                                    loadMessages(group.group)
                                }
                                .background(
                                    if (isSelected) Color(0xFF0000FF).copy(alpha = 0.15f) else Color.Transparent
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = group.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF0000FF) else txtColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (group.group != group.name) {
                                    Text(
                                        text = group.group,
                                        fontSize = 10.sp,
                                        color = subColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 自动发现的群聊
                if (autoGroups.isNotEmpty()) {
                    Divider(color = subColor.copy(alpha = 0.2f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("自动发现", fontSize = 11.sp, color = subColor)
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(autoGroups) { ag ->
                            val name = ag["name"] ?: ""
                            val openid = ag["group_openid"] ?: ""
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // 点击加入
                                        LocalStore.addGroup(name, openid, openid)
                                        loadGroups()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 12.sp,
                                    color = subColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 右侧：消息区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (selectedGroup == null) {
                    // 未选择群聊
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("选择一个群聊", color = subColor, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("或等待消息自动加入", color = subColor.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }
                } else {
                    // 群聊标题
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = selectedGroup!!.name,
                            fontWeight = FontWeight.Bold,
                            color = txtColor,
                            fontSize = 16.sp
                        )
                    }

                    // 消息列表
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        reverseLayout = true
                    ) {
                        items(messages.reversed()) { msg ->
                            MessageItem(msg = msg, isDark = isDark, txtColor = txtColor, subColor = subColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(msg: ChatMessage, isDark: Boolean, txtColor: Color, subColor: Color) {
    val bgColor = if (msg.isSelf) {
        Color(0xFF0000FF).copy(alpha = 0.1f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // 用户名和时间
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = msg.username ?: "未知",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (msg.isSelf) Color(0xFF0000FF) else subColor
            )
            Text(
                text = msg.time,
                fontSize = 10.sp,
                color = subColor.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 消息内容
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            MessageContentBuilder.Build(
                text = msg.content ?: "",
                isDark = isDark
            )
        }
    }
}
