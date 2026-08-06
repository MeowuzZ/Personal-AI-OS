package com.selavie.zhixing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selavie.zhixing.ui.theme.Ink
import com.selavie.zhixing.ui.theme.Muted
import com.selavie.zhixing.ui.theme.PaperDeep
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
fun DateWheelPicker(value: DateWheelValue, label: String = "日期", onValueChange: (DateWheelValue) -> Unit) {
    val hundreds = value.year / 100 % 10
    val tens = value.year / 10 % 10
    val ones = value.year % 10
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
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
            NumberWheel((0..23).toList(), value.hour, "时", Modifier.weight(1f), minimumDigits = 2) { onValueChange(value.copy(hour = it)) }
            NumberWheel((0..59).toList(), value.minute, "分", Modifier.weight(1f), minimumDigits = 2) { onValueChange(value.copy(minute = it)) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberWheel(
    values: List<Int>,
    selected: Int,
    suffix: String,
    modifier: Modifier = Modifier,
    minimumDigits: Int = 1,
    onSelected: (Int) -> Unit,
) {
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = state)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelected by rememberUpdatedState(onSelected)
    val centeredIndex by remember(state) { derivedStateOf { state.nearestCenteredIndex() } }

    LaunchedEffect(state, values) {
        snapshotFlow { state.nearestCenteredIndex() }
            .distinctUntilChanged()
            .collect { index ->
                index?.takeIf { it in values.indices }?.let {
                    val value = values[it]
                    if (value != currentSelected) currentOnSelected(value)
                }
            }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    val target = state.nearestCenteredIndex() ?: return@collect
                    if (state.firstVisibleItemIndex != target || state.firstVisibleItemScrollOffset != 0) {
                        state.animateScrollToItem(target)
                    }
                }
            }
    }
    LaunchedEffect(selectedIndex) {
        if (!state.isScrollInProgress && state.nearestCenteredIndex() != selectedIndex) {
            state.animateScrollToItem(selectedIndex)
        }
    }
    Box(modifier.height(126.dp)) {
        Box(
            Modifier.fillMaxWidth().height(42.dp).align(Alignment.Center).clip(RoundedCornerShape(12.dp))
                .background(PaperDeep.copy(alpha = .55f)),
        )
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 42.dp),
            flingBehavior = snapFlingBehavior,
        ) {
            items(values.size) { index ->
                val item = values[index]
                val distanceFromCenter by remember(state, index) { derivedStateOf { state.distanceFromCenter(index) } }
                val scale = (1f - distanceFromCenter * .18f).coerceIn(.7f, 1f)
                val alpha = (1f - distanceFromCenter * .5f).coerceIn(.18f, 1f)
                val centered = centeredIndex == index
                Row(
                    Modifier.fillMaxWidth().height(42.dp)
                        .clickable { scope.launch { state.animateScrollToItem(index) } }
                        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        item.toString().padStart(minimumDigits, '0'),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (centered) Ink else Muted,
                        fontWeight = if (centered) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                    if (suffix.isNotEmpty()) Text(suffix, style = MaterialTheme.typography.labelMedium, color = Muted)
                }
            }
        }
    }
}

private fun LazyListState.nearestCenteredIndex(): Int? {
    val layout = layoutInfo
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
    return layout.visibleItemsInfo.minByOrNull { item -> abs(item.offset + item.size / 2 - center) }?.index
}

private fun LazyListState.distanceFromCenter(index: Int): Float {
    val layout = layoutInfo
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
    val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return 2f
    return abs(item.offset + item.size / 2 - center).toFloat() / item.size.coerceAtLeast(1)
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
