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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        return try { Color(h.toLong(16)) } catch (_: Exception) { Color.White }
    }

    private fun braceAt(s: String, start: Int): String? {
        if (start >= s.length || s[start] != '{') return null
        var depth = 0; var i = start
        while (i < s.length) {
            when (s[i]) { '{' -> depth++; '}' -> { depth--; if (depth == 0) return s.substring(start + 1, i) } }
            i++
        }
        return null
    }

    @Composable
    fun Build(text: String, isDark: Boolean, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
        val txtColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.9f)
        val subColor = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)
        val bubbleColor = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)

        val codeParts = text.split(Regex("```(?:\\w*)\\n?"))
        val allComposables = mutableListOf<@Composable () -> Unit>()

        for ((i, part) in codeParts.withIndex()) {
            if (part.isBlank()) continue
            if (i % 2 == 1) {
                allComposables.add {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(bubbleColor, RoundedCornerShape(8.dp)).padding(10.dp)) {
                        Text(text = part.trim(), style = TextStyle(fontSize = 13.sp, color = txtColor, fontFamily = FontFamily.Monospace, lineHeight = 19.5.sp))
                    }
                }
            } else {
                allComposables.add { LatexLine(text = part, txtColor = txtColor, subColor = subColor, textAlign = textAlign) }
            }
        }

        Column(modifier = modifier) { allComposables.forEach { it() } }
    }

    @Composable
    private fun LatexLine(text: String, txtColor: Color, subColor: Color, textAlign: TextAlign) {
        val spans = mutableListOf<AnnotatedString.Builder.() -> Unit>()
        val widgets = mutableListOf<@Composable () -> Unit>()
        var i = 0

        while (i < text.length) {
            // markdown 图片
            if (i + 1 < text.length && text.substring(i, i + 2) == "![") {
                val cb = text.indexOf(']', i + 2)
                if (cb >= 0 && cb + 1 < text.length && text[cb + 1] == '(') {
                    val cp = text.indexOf(')', cb + 2)
                    if (cp > cb + 2) {
                        val url = text.substring(cb + 2, cp); i = cp + 1
                        flushSpans(spans, widgets, txtColor, textAlign)
                        widgets.add { Box(modifier = Modifier.padding(vertical = 4.dp).size(200.dp, 100.dp).background(subColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text("📷 $url", color = subColor, fontSize = 10.sp) } }
                        continue
                    }
                }
            }

            // markdown 链接
            if (i < text.length && text[i] == '[' && (i == 0 || text[i - 1] != '!')) {
                val cb = text.indexOf(']', i + 1)
                if (cb >= 0 && cb + 1 < text.length && text[cb + 1] == '(') {
                    val cp = text.indexOf(')', cb + 2)
                    if (cp > cb + 2) {
                        val linkText = text.substring(i + 1, cb); i = cp + 1
                        flushSpans(spans, widgets, txtColor, textAlign)
                        widgets.add { Text(text = linkText, style = TextStyle(fontSize = 14.sp, color = Color(0xFF64B5F6), textDecoration = TextDecoration.Underline)) }
                        continue
                    }
                }
            }

            // \big{#bg}{#fg}{text}
            if (i + 4 < text.length && text.substring(i, i + 4) == "\\big") {
                val bs = text.indexOf('{', i + 4)
                if (bs >= 0) {
                    val inner = braceAt(text, bs)
                    if (inner != null) {
                        val parts = mutableListOf<String>(); var pos = 0
                        while (pos < inner.length) {
                            val nb = inner.indexOf('{', pos)
                            if (nb < 0) { parts.add(inner.substring(pos)); break }
                            if (nb > pos) parts.add(inner.substring(pos, nb))
                            val inn = braceAt(inner, nb)
                            if (inn != null) { parts.add(inn); pos = nb + inn.length + 2 } else { parts.add(inner.substring(nb)); break }
                        }
                        if (parts.size >= 3) {
                            val bg = parts[0].trim(); val fg = parts[1].trim()
                            val display: String; val scale: Double
                            if (parts.size >= 4) { scale = parts[2].trim().toDoubleOrNull() ?: 2.0; display = parts[3].trim() } else { scale = 2.0; display = parts[2].trim() }
                            i = bs + inner.length + 2
                            flushSpans(spans, widgets, txtColor, textAlign)
                            widgets.add { Box(modifier = Modifier.padding(vertical = 4.dp).background(parseHex(bg), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) { Text(text = display, style = TextStyle(fontSize = (14 * scale).sp, color = parseHex(fg), fontWeight = FontWeight.Bold)) } }
                            continue
                        }
                    }
                }
            }

            // \colorbox{#hex}{content}
            if (i + 10 < text.length && text.substring(i, i + 10) == "\\colorbox{") {
                val he = text.indexOf('}', i + 10)
                if (he > i + 10) {
                    val bgHex = text.substring(i + 10, he)
                    val ob = text.indexOf('{', he + 1)
                    if (ob >= 0) {
                        val outer = braceAt(text, ob)
                        if (outer != null) {
                            var display = outer; val lb = outer.lastIndexOf('{')
                            if (lb >= 0) { val inn = braceAt(outer, lb); if (inn != null) display = inn }
                            i = ob + outer.length + 2
                            spans.add { withAnnotation("colorbox", bgHex) { append(display) } }
                            continue
                        }
                    }
                }
            }

            // \textcolor{#hex}{text}
            if (i + 11 < text.length && text.substring(i, i + 11) == "\\textcolor{") {
                val he = text.indexOf('}', i + 11)
                if (he > i + 11) {
                    val fgHex = text.substring(i + 11, he)
                    val content = braceAt(text, he + 1)
                    if (content != null) {
                        i = he + 1 + content.length + 2
                        spans.add { withStyle(SpanStyle(color = parseHex(fgHex))) { append(content) } }
                        continue
                    }
                }
            }

            // \Huge text
            if (i + 5 <= text.length && text.substring(i, i + 5) == "\\Huge") {
                var j = i + 5; if (j < text.length && text[j] == ' ') j++
                val nb = text.indexOf('\\', j)
                val hugeText = if (nb >= 0) text.substring(j, nb).trim() else text.substring(j).trim()
                i = if (nb >= 0) nb else text.length
                if (hugeText.isNotEmpty()) {
                    flushSpans(spans, widgets, txtColor, textAlign)
                    widgets.add { Text(text = hugeText, style = TextStyle(fontSize = 24.sp, color = txtColor, fontWeight = FontWeight.Bold, lineHeight = 33.6.sp)) }
                }
                continue
            }

            // 普通文字
            val nc = text.indexOf('\\', i + 1)
            if (nc > i) { spans.add { append(text.substring(i, nc)) }; i = nc }
            else if (nc == i) { i++ }
            else { spans.add { append(text.substring(i)) }; break }
        }

        flushSpans(spans, widgets, txtColor, textAlign)
        Column { widgets.forEach { it() } }
    }

    private fun flushSpans(spans: MutableList<AnnotatedString.Builder.() -> Unit>, widgets: MutableList<@Composable () -> Unit>, txtColor: Color, textAlign: TextAlign) {
        if (spans.isNotEmpty()) {
            val builder = AnnotatedString.Builder(); spans.forEach { it(builder) }
            widgets.add { Text(text = builder.toAnnotatedString(), style = TextStyle(fontSize = 14.sp, color = txtColor, lineHeight = 19.6.sp), textAlign = textAlign) }
            spans.clear()
        }
    }
}
