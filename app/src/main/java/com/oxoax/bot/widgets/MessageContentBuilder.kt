@file:OptIn(ExperimentalTextApi::class)

package com.oxoax.bot.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.*
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 消息内容渲染器 — 处理彩色文字、代码块、LaTeX 语法、图片等
 * 从 Flutter MessageContentBuilder 迁移
 */
object MessageContentBuilder {

    private val namedColors = mapOf(
        "black" to 0xFF000000,
        "white" to 0xFFFFFFFF,
        "red" to 0xFFFF0000,
        "green" to 0xFF00FF00,
        "blue" to 0xFF0000FF,
        "yellow" to 0xFFFFFF00,
        "orange" to 0xFFFFA500,
        "purple" to 0xFF800080,
        "pink" to 0xFFFFC0CB,
        "gray" to 0xFF808080,
        "grey" to 0xFF808080
    )

    private fun parseHex(hex: String): Color {
        var h = hex.removePrefix("#").lowercase()
        namedColors[h]?.let { return Color(it) }
        if (h.length == 6) h = "FF$h"
        return try {
            Color(h.toLong(16))
        } catch (_: Exception) {
            Color.White
        }
    }

    private fun braceAt(s: String, start: Int): String? {
        if (start >= s.length || s[start] != '{') return null
        var depth = 0
        var i = start
        while (i < s.length) {
            when (s[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return s.substring(start + 1, i)
                }
            }
            i++
        }
        return null
    }

    @Composable
    fun Build(
        text: String,
        isDark: Boolean,
        modifier: Modifier = Modifier,
        textAlign: TextAlign = TextAlign.Start
    ) {
        val txtColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.9f)
        val subColor = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)
        val bubbleColor = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)

        // 分割代码块
        val codeParts = text.split(Regex("```(?:\\w*)\\n?"))
        val allComposables = mutableListOf<@Composable () -> Unit>()

        for ((i, part) in codeParts.withIndex()) {
            if (part.isBlank()) continue
            if (i % 2 == 1) {
                // 代码块
                allComposables.add {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(bubbleColor, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = part.trim(),
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = txtColor,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 19.5.sp
                            )
                        )
                    }
                }
            } else {
                allComposables.add {
                    LatexLine(
                        text = part,
                        txtColor = txtColor,
                        subColor = subColor,
                        textAlign = textAlign
                    )
                }
            }
        }

        Column(modifier = modifier) {
            allComposables.forEach { it() }
        }
    }

    @Composable
    private fun LatexLine(
        text: String,
        txtColor: Color,
        subColor: Color,
        textAlign: TextAlign
    ) {
        val spans = mutableListOf<AnnotatedString.Builder.() -> Unit>()
        val widgets = mutableListOf<@Composable () -> Unit>()
        var i = 0

        while (i < text.length) {
            // markdown 图片 ![alt](url)
            if (i + 1 < text.length && text.substring(i, i + 2) == "![") {
                val closeBracket = text.indexOf(']', i + 2)
                if (closeBracket >= 0 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 2) {
                        val url = text.substring(closeBracket + 2, closeParen)
                        i = closeParen + 1
                        // 先输出已有 spans
                        if (spans.isNotEmpty()) {
                            val builder = AnnotatedString.Builder()
                            spans.forEach { it(builder) }
                            widgets.add {
                                Text(
                                    text = builder.toAnnotatedString(),
                                    style = TextStyle(fontSize = 14.sp, color = txtColor, lineHeight = 19.6.sp),
                                    textAlign = textAlign
                                )
                            }
                            spans.clear()
                        }
                        widgets.add {
                            // 图片占位（实际项目中用 Coil 等加载）
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .size(200.dp, 100.dp)
                                    .background(subColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("📷 $url", color = subColor, fontSize = 10.sp)
                            }
                        }
                        continue
                    }
                }
            }

            // markdown 链接 [text](url)
            if (i < text.length && text[i] == '[' && (i == 0 || text[i - 1] != '!')) {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket >= 0 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 2) {
                        val linkText = text.substring(i + 1, closeBracket)
                        i = closeParen + 1
                        spans.add {
                            withStyle(SpanStyle(color = Color(0xFF1565C0))) {
                                append(linkText)
                            }
                        }
                        continue
                    }
                }
            }

            // \gradient{#hex1}{#hex2}{text}
            if (i + 9 < text.length && text.substring(i, i + 9) == "\\gradient") {
                val brace1 = text.indexOf('{', i + 9)
                if (brace1 >= 0) {
                    val hex1 = braceAt(text, brace1)
                    if (hex1 != null) {
                        val brace2 = text.indexOf('{', brace1 + hex1.length + 2)
                        if (brace2 >= 0) {
                            val hex2 = braceAt(text, brace2)
                            if (hex2 != null) {
                                val brace3 = text.indexOf('{', brace2 + hex2.length + 2)
                                if (brace3 >= 0) {
                                    val content = braceAt(text, brace3)
                                    if (content != null) {
                                        i = brace3 + content.length + 2
                                        // 先输出已有 spans
                                        if (spans.isNotEmpty()) {
                                            val builder = AnnotatedString.Builder()
                                            spans.forEach { it(builder) }
                                            widgets.add {
                                                Text(
                                                    text = builder.toAnnotatedString(),
                                                    style = TextStyle(fontSize = 14.sp, color = txtColor, lineHeight = 19.6.sp),
                                                    textAlign = textAlign
                                                )
                                            }
                                            spans.clear()
                                        }
                                        widgets.add {
                                            Text(
                                                text = content,
                                                style = TextStyle(
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(parseHex(hex1), parseHex(hex2))
                                                    ),
                                                    lineHeight = 19.6.sp
                                                )
                                            )
                                        }
                                        continue
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // \big{#bg}{#fg}{text} or \big{#bg}{#fg}{scale}{text}
            if (i + 4 < text.length && text.substring(i, i + 4) == "\\big") {
                val braceStart = text.indexOf('{', i + 4)
                if (braceStart >= 0) {
                    val innerContent = braceAt(text, braceStart)
                    if (innerContent != null) {
                        val parts = mutableListOf<String>()
                        var pos = 0
                        while (pos < innerContent.length) {
                            val nextBrace = innerContent.indexOf('{', pos)
                            if (nextBrace < 0) {
                                parts.add(innerContent.substring(pos))
                                break
                            }
                            if (nextBrace > pos) parts.add(innerContent.substring(pos, nextBrace))
                            val inner = braceAt(innerContent, nextBrace)
                            if (inner != null) {
                                parts.add(inner)
                                pos = nextBrace + inner.length + 2
                            } else {
                                parts.add(innerContent.substring(nextBrace))
                                break
                            }
                        }

                        if (parts.size >= 3) {
                            val bg = parts[0].trim()
                            val fg = parts[1].trim()
                            val display: String
                            val scale: Double
                            if (parts.size >= 4) {
                                scale = parts[2].trim().toDoubleOrNull() ?: 2.0
                                display = parts[3].trim()
                            } else {
                                scale = 2.0
                                display = parts[2].trim()
                            }
                            i = braceStart + innerContent.length + 2

                            // 先输出已有 spans
                            if (spans.isNotEmpty()) {
                                val builder = AnnotatedString.Builder()
                                spans.forEach { it(builder) }
                                widgets.add {
                                    Text(
                                        text = builder.toAnnotatedString(),
                                        style = TextStyle(fontSize = 14.sp, color = txtColor, lineHeight = 19.6.sp),
                                        textAlign = textAlign
                                    )
                                }
                                spans.clear()
                            }
                            widgets.add {
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 4.dp)
                                        .background(parseHex(bg), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = display,
                                        style = TextStyle(
                                            fontSize = (14 * scale).sp,
                                            color = parseHex(fg),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                            continue
                        }
                    }
                }
            }

            // \colorbox{#hex}{content}
            if (i + 10 < text.length && text.substring(i, i + 10) == "\\colorbox{") {
                val hexEnd = text.indexOf('}', i + 10)
                if (hexEnd > i + 10) {
                    val bgHex = text.substring(i + 10, hexEnd)
                    val outerBrace = text.indexOf('{', hexEnd + 1)
                    if (outerBrace >= 0) {
                        val outer = braceAt(text, outerBrace)
                        if (outer != null) {
                            var display = outer
                            val lastBrace = outer.lastIndexOf('{')
                            if (lastBrace >= 0) {
                                val inner = braceAt(outer, lastBrace)
                                if (inner != null) display = inner
                            }
                            i = outerBrace + outer.length + 2
                            spans.add {
                                withAnnotation("colorbox", bgHex) {
                                    append(display)
                                }
                            }
                            continue
                        }
                    }
                }
            }

            // \textcolor{#hex}{text}
            if (i + 11 < text.length && text.substring(i, i + 11) == "\\textcolor{") {
                val hexEnd = text.indexOf('}', i + 11)
                if (hexEnd > i + 11) {
                    val fgHex = text.substring(i + 11, hexEnd)
                    val content = braceAt(text, hexEnd + 1)
                    if (content != null) {
                        i = hexEnd + 1 + content.length + 2
                        spans.add {
                            withStyle(SpanStyle(color = parseHex(fgHex))) {
                                append(content)
                            }
                        }
                        continue
                    }
                }
            }

            // \Huge text
            if (i + 5 <= text.length && text.substring(i, i + 5) == "\\Huge") {
                var j = i + 5
                if (j < text.length && text[j] == ' ') j++
                val nextB = text.indexOf('\\', j)
                val hugeText = if (nextB >= 0) text.substring(j, nextB).trim() else text.substring(j).trim()
                i = if (nextB >= 0) nextB else text.length
                if (hugeText.isNotEmpty()) {
                    // 先输出已有 spans
                    if (spans.isNotEmpty()) {
                        val builder = AnnotatedString.Builder()
                        spans.forEach { it(builder) }
                        widgets.add {
                            Text(
                                text = builder.toAnnotatedString(),
                                style = TextStyle(fontSize = 14.sp, color = txtColor, lineHeight = 19.6.sp),
                                textAlign = textAlign
                            )
                        }
                        spans.clear()
                    }
                    widgets.add {
                        Text(
                            text = hugeText,
                            style = TextStyle(
                                fontSize = 24.sp,
                                color = txtColor,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 33.6.sp
                            )
                        )
                    }
                }
                continue
            }

            // 普通文字
            val nextCmd = text.indexOf('\\', i + 1)
            if (nextCmd > i) {
                spans.add { append(text.substring(i, nextCmd)) }
                i = nextCmd
            } else if (nextCmd == i) {
                i++
            } else {
                spans.add { append(text.substring(i)) }
                break
            }
        }

        // 输出剩余 spans
        if (spans.isNotEmpty()) {
            val builder = AnnotatedString.Builder()
            spans.forEach { it(builder) }
            widgets.add {
                Text(
                    text = builder.toAnnotatedString(),
                    style = TextStyle(fontSize = 14.sp, color = txtColor, lineHeight = 19.6.sp),
                    textAlign = textAlign
                )
            }
        }

        Column {
            widgets.forEach { it() }
        }
    }
}
