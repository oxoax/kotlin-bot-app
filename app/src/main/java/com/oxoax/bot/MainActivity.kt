package com.oxoax.bot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oxoax.bot.services.GlobalWs
import com.oxoax.bot.services.LocalStore
import com.oxoax.bot.services.MessageStore
import com.oxoax.bot.widgets.MessageContentBuilder
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化服务
        LocalStore.init(this)
        MessageStore.init(this)
        GlobalWs.init()

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!LocalStore.isConfigured) {
                        SetupScreen()
                    } else {
                        MainScreen()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GlobalWs.dispose()
    }
}

@Composable
fun SetupScreen() {
    var botId by remember { mutableStateOf("") }
    var botSecret by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("XBOT 设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = botId,
            onValueChange = { botId = it },
            label = { Text("App ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = botSecret,
            onValueChange = { botSecret = it },
            label = { Text("App Secret") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (botId.isNotEmpty() && botSecret.isNotEmpty()) {
                    LocalStore.saveBot(botId, botSecret)
                    GlobalWs.init()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存并连接")
        }
    }
}

@Composable
fun MainScreen() {
    var isConnected by remember { mutableStateOf(GlobalWs.isConnected) }
    var messages by remember { mutableStateOf(listOf<String>()) }
    val isDark = isSystemInDarkTheme()

    DisposableEffect(Unit) {
        val connListener: (Boolean) -> Unit = { connected -> isConnected = connected }
        val eventListener: (JSONObject) -> Unit = { event ->
            val parsed = com.oxoax.bot.services.QQWsService("", "").parseEvent(event)
            if (parsed != null) {
                messages = messages + "[${parsed.authorName}]: ${parsed.content}"
            }
        }
        GlobalWs.addConnectionListener(connListener)
        GlobalWs.addEventListener(eventListener)
        onDispose {
            GlobalWs.removeConnectionListener(connListener)
            GlobalWs.removeEventListener(eventListener)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 状态栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isConnected) "已连接" else "未连接",
                color = Color.White
            )
        }

        // 消息列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            items(messages) { msg ->
                MessageContentBuilder.Build(
                    text = msg,
                    isDark = isDark,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // 测试区域：展示各种文字效果
        Column(modifier = Modifier.padding(16.dp)) {
            Text("效果演示:", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            // 普通文字
            MessageContentBuilder.Build(text = "这是普通文字", isDark = isDark)

            // 彩色文字
            MessageContentBuilder.Build(
                text = "\\textcolor{#FF6B6B}{红色文字} 和 \\textcolor{#4ECDC4}{青色文字}",
                isDark = isDark
            )

            // 渐变文字
            MessageContentBuilder.Build(
                text = "\\gradient{#FF6B6B}{#4ECDC4}{渐变文字效果}",
                isDark = isDark
            )

            // 大字
            MessageContentBuilder.Build(
                text = "\\Huge 这是大字效果",
                isDark = isDark
            )

            // 背景色块
            MessageContentBuilder.Build(
                text = "\\colorbox{#2D3436}{\\textcolor{#FFFFFF}{深色背景白色文字}}",
                isDark = isDark
            )

            // big 效果
            MessageContentBuilder.Build(
                text = "\\big{#6C5CE7}{#FFFFFF}{2}{重要提示}",
                isDark = isDark
            )

            // 代码块
            MessageContentBuilder.Build(
                text = "```kotlin\nfun main() {\n    println(\"Hello, World!\")\n}\n```",
                isDark = isDark
            )
        }
    }
}
