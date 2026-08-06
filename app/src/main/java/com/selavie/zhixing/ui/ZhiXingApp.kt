package com.selavie.zhixing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.selavie.zhixing.AppController
import com.selavie.zhixing.model.TaskPhase
import com.selavie.zhixing.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

enum class AppScreen(val label: String) {
    TODAY("今日"), TASKS("任务"), KNOWLEDGE("日记"), GOALS("目标"), ASSISTANT("助手"),
    CALENDAR("日历"), PROFILE("个人信息"),
}

private val primaryScreens = listOf(AppScreen.TODAY, AppScreen.TASKS, AppScreen.KNOWLEDGE, AppScreen.GOALS, AppScreen.ASSISTANT)

@Composable
fun ZhiXingApp(
    controller: AppController,
    onExport: () -> Unit,
    onBirthdayChanged: (String) -> Unit,
) {
    if (!controller.data.preferences.onboarded) {
        OnboardingScreen(onEmpty = controller::beginEmpty, onDemo = controller::beginDemo)
        return
    }

    var screenName by rememberSaveable { mutableStateOf(AppScreen.TODAY.name) }
    val screen = AppScreen.valueOf(screenName)
    var captureOpen by rememberSaveable { mutableStateOf(false) }
    var overdueReminderOpen by remember { mutableStateOf(true) }
    var requestedReviewDate by remember { mutableStateOf<String?>(null) }
    val clock by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) overdueReminderOpen = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val navigate: (AppScreen) -> Unit = { screenName = it.name }

    Box(Modifier.fillMaxSize().background(Paper).statusBarsPadding()) {
        Scaffold(
            containerColor = Paper,
            bottomBar = { TextOnlyNavigation(screen = screen, motto = controller.data.preferences.motto, onNavigate = navigate) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { captureOpen = true },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    containerColor = Ink,
                    contentColor = Color.White,
                ) { Text("+", fontSize = 30.sp, fontWeight = FontWeight.Light) }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                when (screen) {
                    AppScreen.TODAY -> TodayScreen(
                        controller = controller,
                        navigate = navigate,
                        now = clock,
                        onDailyReview = { date ->
                            requestedReviewDate = date.toString()
                            navigate(AppScreen.ASSISTANT)
                        },
                    )
                    AppScreen.TASKS -> TasksScreen(controller, clock)
                    AppScreen.KNOWLEDGE -> DiaryScreen(controller)
                    AppScreen.GOALS -> GoalsScreen(controller)
                    AppScreen.ASSISTANT -> AssistantScreen(
                        controller = controller,
                        requestedReviewDate = requestedReviewDate,
                        onReviewOpened = { requestedReviewDate = null },
                    )
                    AppScreen.CALENDAR -> CalendarScreen(controller, clock, onBack = { navigate(AppScreen.TODAY) })
                    AppScreen.PROFILE -> ProfileScreen(
                        controller = controller,
                        onBack = { navigate(AppScreen.TODAY) },
                        onExport = onExport,
                        onBirthdayChanged = onBirthdayChanged,
                    )
                }
            }
        }
    }

    if (captureOpen) {
        CaptureDialog(
            engine = controller.smartEngine,
            onDismiss = { captureOpen = false },
            onConfirm = {
                controller.handleCapture(it)
                captureOpen = false
            },
        )
    }

    val overdue = controller.data.tasks.filter { controller.smartEngine.taskPhase(it, clock) == TaskPhase.OVERDUE }
    if (overdueReminderOpen && overdue.isNotEmpty()) {
        OverdueReminderDialog(
            tasks = overdue,
            onComplete = controller::toggleTask,
            onDismiss = { overdueReminderOpen = false },
        )
    }
}

@Composable
private fun TextOnlyNavigation(screen: AppScreen, motto: String, onNavigate: (AppScreen) -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                primaryScreens.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (screen == item) Lime else Color.Transparent)
                            .clickable { onNavigate(item) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (screen == item) Ink else Muted,
                        )
                    }
                }
            }
            Text(
                motto.ifBlank { "把想法变成今天的行动" },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Muted,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
