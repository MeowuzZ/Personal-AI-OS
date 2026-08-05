package com.selavie.zhixing

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.selavie.zhixing.data.AppRepository
import com.selavie.zhixing.reminder.BirthdayReminder
import com.selavie.zhixing.ui.ZhiXingApp
import com.selavie.zhixing.ui.theme.ZhiXingTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        BirthdayReminder.schedule(applicationContext, AppRepository(applicationContext).load().preferences.birthday)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        BirthdayReminder.ensureChannel(applicationContext)
        BirthdayReminder.schedule(applicationContext, AppRepository(applicationContext).load().preferences.birthday)
        setContent {
            ZhiXingTheme {
                val controller = remember { AppController(AppRepository(applicationContext)) }
                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json"),
                ) { uri ->
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { stream ->
                            stream.bufferedWriter().use { writer ->
                                writer.write(controller.exportJson())
                            }
                        }
                    }
                }
                val notificationPermission = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { }
                ZhiXingApp(
                    controller = controller,
                    onExport = { exportLauncher.launch("知行数据-${LocalDate.now()}.json") },
                    onBirthdayChanged = { birthday ->
                        BirthdayReminder.schedule(applicationContext, birthday)
                        val validBirthday = runCatching { LocalDate.parse(birthday) }.isSuccess
                        if (validBirthday && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (validBirthday && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val alarms = getSystemService(AlarmManager::class.java)
                            if (!alarms.canScheduleExactAlarms()) {
                                startActivity(Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:$packageName"),
                                ))
                            }
                        }
                    },
                )
            }
        }
    }
}
