package com.selavie.zhixing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selavie.zhixing.ui.theme.Ink
import com.selavie.zhixing.ui.theme.Muted
import com.selavie.zhixing.ui.theme.PaperDeep
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

data class DateWheelValue(val year: Int, val month: Int, val day: Int) {
    fun toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.of(year, month, day) }.getOrNull()

    companion object {
        fun from(raw: String?, fallback: LocalDate = LocalDate.now()): DateWheelValue {
            val parsed = runCatching { LocalDate.parse(raw) }.getOrNull() ?: fallback
            return DateWheelValue(parsed.year.coerceIn(2000, 2999), parsed.monthValue, parsed.dayOfMonth)
        }
    }
}

data class TimeWheelValue(val hour: Int, val minute: Int) {
    fun asText(): String = "%02d:%02d".format(hour, minute)
    fun toLocalTime(): LocalTime = LocalTime.of(hour, minute)

    companion object {
        fun from(raw: String?, fallback: LocalTime = LocalTime.now()): TimeWheelValue {
            val parsed = runCatching { LocalTime.parse(raw) }.getOrNull() ?: fallback
            return TimeWheelValue(parsed.hour, parsed.minute)
        }
    }
}

@Composable
fun DateWheelPicker(value: DateWheelValue, onValueChange: (DateWheelValue) -> Unit) {
    val hundreds = value.year / 100 % 10
    val tens = value.year / 10 % 10
    val ones = value.year % 10
    Column {
        Text("日期", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            FixedWheelValue("2", "", Modifier.weight(.55f))
            NumberWheel((0..9).toList(), hundreds, "", Modifier.weight(.55f)) {
                onValueChange(value.copy(year = 2000 + it * 100 + tens * 10 + ones))
            }
            NumberWheel((0..9).toList(), tens, "", Modifier.weight(.55f)) {
                onValueChange(value.copy(year = 2000 + hundreds * 100 + it * 10 + ones))
            }
            NumberWheel((0..9).toList(), ones, "年", Modifier.weight(.65f)) {
                onValueChange(value.copy(year = 2000 + hundreds * 100 + tens * 10 + it))
            }
            NumberWheel((1..12).toList(), value.month, "月", Modifier.weight(.9f)) { onValueChange(value.copy(month = it)) }
            NumberWheel((1..31).toList(), value.day, "日", Modifier.weight(.9f)) { onValueChange(value.copy(day = it)) }
        }
    }
}

@Composable
fun TimeWheelPicker(label: String, value: TimeWheelValue, onValueChange: (TimeWheelValue) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            NumberWheel((0..23).toList(), value.hour, "时", Modifier.weight(1f)) { onValueChange(value.copy(hour = it)) }
            NumberWheel((0..59).toList(), value.minute, "分", Modifier.weight(1f)) { onValueChange(value.copy(minute = it)) }
        }
    }
}

@Composable
private fun FixedWheelValue(value: String, suffix: String, modifier: Modifier = Modifier) {
    Row(modifier.height(126.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        if (suffix.isNotEmpty()) Text(suffix, style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}

@Composable
private fun NumberWheel(
    values: List<Int>,
    selected: Int,
    suffix: String,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit,
) {
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    LaunchedEffect(state, values) {
        snapshotFlow {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { item -> abs(item.offset + item.size / 2 - center) }?.index
        }
            .distinctUntilChanged()
            .collect { index -> index?.takeIf { it in values.indices }?.let { onSelected(values[it]) } }
    }
    LaunchedEffect(selectedIndex) {
        if (!state.isScrollInProgress && state.firstVisibleItemIndex != selectedIndex) {
            state.scrollToItem(selectedIndex)
        }
    }
    Box(modifier.height(126.dp)) {
        Box(
            Modifier.fillMaxWidth().height(42.dp).align(Alignment.Center).clip(RoundedCornerShape(10.dp)).background(PaperDeep),
        )
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 42.dp),
        ) {
            items(values.size) { index ->
                val item = values[index]
                Row(
                    Modifier.fillMaxWidth().height(42.dp).clickable {
                        onSelected(item)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        item.toString().padStart(if (values.size > 31) 2 else 1, '0'),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item == selected) Ink else Muted,
                        fontWeight = if (item == selected) FontWeight.ExtraBold else FontWeight.Normal,
                    )
                    if (suffix.isNotEmpty()) Text(suffix, style = MaterialTheme.typography.labelMedium, color = Muted)
                }
            }
        }
    }
}

@Composable
fun InvalidDateDialog(onDismiss: () -> Unit) {
    AlertMessageDialog("日期不存在", "请选择真实存在的月份日期后再确认。", onDismiss)
}

@Composable
fun AlertMessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}
