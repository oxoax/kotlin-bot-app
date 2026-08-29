package com.oxoax.bot

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
        setContent { App() }
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

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景图
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("https://www.loliapi.com/acg/pe/")
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
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
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // 输入框
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        cornerRadius = 12.dp
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (name.isEmpty()) {
                                        Text(
                                            "请输入名称",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    // 输入框
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        cornerRadius = 12.dp
                    ) {
                        BasicTextField(
                            value = qq,
                            onValueChange = { qq = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (qq.isEmpty()) {
                                        Text(
                                            "请输入QQ号",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    // 输入框
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        cornerRadius = 12.dp
                    ) {
                        BasicTextField(
                            value = botId,
                            onValueChange = { botId = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (botId.isEmpty()) {
                                        Text(
                                            "请输入机器人ID",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    // 输入框
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        cornerRadius = 12.dp
                    ) {
                        BasicTextField(
                            value = botSecret,
                            onValueChange = { botSecret = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (botSecret.isEmpty()) {
                                        Text(
                                            "请输入机器人密钥",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    // 登录按钮
                    LiquidGlassCapsule(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable(enabled = !saving) {
                                if (name.isNotBlank() && qq.isNotBlank() && botId.isNotBlank() && botSecret.isNotBlank()) {
                                    saving = true
                                    scope.launch {
                                        try {
                                            // 保存配置
                                            LocalStore.saveConfig(name, qq, botId, botSecret)
                                            withContext(Dispatchers.Main) {
                                                onDone()
                                            }
                                        } catch (e: Exception) {
                                            Log.e("LoginPage", "保存配置失败", e)
                                        } finally {
                                            saving = false
                                        }
                                    }
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "登录",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 主页面 ====================

@Composable
fun MainPage() {
    var showConfig by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: 群聊, 1: 私聊
    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 加载群列表
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                groups = ApiClient.getGroups()
            } catch (e: Exception) {
                Log.e("MainPage", "加载群列表失败", e)
            }
        }
    }

    // 加载消息
    LaunchedEffect(selectedGroup) {
        selectedGroup?.let { group ->
            scope.launch {
                try {
                    messages = ApiClient.getMessages(group.id)
                    // 滚动到底部
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                } catch (e: Exception) {
                    Log.e("MainPage", "加载消息失败", e)
                }
            }
        }
    }

    // 发送消息
    fun sendMessage() {
        if (inputText.isBlank() || sending) return
        sending = true
        scope.launch {
            try {
                selectedGroup?.let { group ->
                    ApiClient.sendMessage(group.id, inputText)
                    inputText = ""
                    // 重新加载消息
                    messages = ApiClient.getMessages(group.id)
                    // 滚动到底部
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainPage", "发送消息失败", e)
            } finally {
                sending = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景图
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("https://www.loliapi.com/acg/pe/")
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 毛玻璃遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )

        // 内容
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏 - 液态玻璃
            LiquidGlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 头像
                    LiquidGlassCircle(modifier = Modifier.size(40.dp)) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // 标题
                    Text(
                        "XBOT",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 设置按钮
                    LiquidGlassCircle(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { showConfig = true }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 群列表
            if (selectedGroup == null) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                ) {
                    items(groups) { group ->
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedGroup = group },
                            cornerRadius = 16.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 群头像
                                LiquidGlassCircle(modifier = Modifier.size(48.dp)) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            group.name.take(1),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        group.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        group.id,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 消息列表
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    state = listState
                ) {
                    items(messages) { message ->
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            cornerRadius = 16.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        message.senderName,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        message.time,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 10.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    message.content,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // 输入框 - 液态玻璃
                LiquidGlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    cornerRadius = 20.dp
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 返回按钮
                        LiquidGlassCircle(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { selectedGroup = null }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "←",
                                    color = Color.White,
                                    fontSize = 20.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // 输入框
                        LiquidGlassCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 20.dp
                        ) {
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                singleLine = true,
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // 发送按钮
                        LiquidGlassCircle(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(enabled = inputText.isNotBlank() && !sending) {
                                    sendMessage()
                                }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = if (inputText.isNotBlank()) Color.White else Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 底部栏 - 液态玻璃
            LiquidGlassBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                cornerRadius = 20.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 群列表图标
                    LiquidGlassCapsule(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "群",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                    // 分割线
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(24.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                    // 私列表图标
                    LiquidGlassCapsule(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "私",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // 搜索
                    LiquidGlassCircle(modifier = Modifier.size(36.dp)) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // 设置
                    LiquidGlassCircle(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { showConfig = true }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 配置弹窗
        if (showConfig) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showConfig = false }
            ) {
                LiquidGlassBox(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                        .fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            "机器人配置",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "ID: ${LocalStore.botId ?: ""}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                        Text(
                            "QQ: ${LocalStore.botQQ ?: ""}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                        Text(
                            "名称: ${LocalStore.botName ?: ""}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LiquidGlassCapsule(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable { showConfig = false }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "关闭",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
