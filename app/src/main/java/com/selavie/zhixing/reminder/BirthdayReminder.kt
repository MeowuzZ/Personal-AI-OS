package com.selavie.zhixing.reminder

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import com.selavie.zhixing.MainActivity
import com.selavie.zhixing.data.AppRepository
import java.time.*
import java.util.Calendar

object BirthdayReminder {
    const val CHANNEL_ID = "birthday_wishes"
    private const val ACTION = "com.selavie.zhixing.BIRTHDAY_REMINDER"
    private const val REQUEST_CODE = 805
    private const val NOTIFICATION_ID = 805

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            "生日祝福",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "在生日当天上午 8:00 发送本地祝福" })
    }

    fun schedule(context: Context, birthday: String) {
        cancel(context)
        val birthDate = runCatching { LocalDate.parse(birthday) }.getOrNull() ?: return
        val trigger = nextBirthdayAtEight(birthDate, ZonedDateTime.now())
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = alarmIntent(context)
        val millis = trigger.toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }

    internal fun nextBirthdayAtEight(birthday: LocalDate, now: ZonedDateTime): ZonedDateTime {
        var year = now.year
        while (year <= now.year + 8) {
            val candidate = runCatching {
                ZonedDateTime.of(LocalDate.of(year, birthday.monthValue, birthday.dayOfMonth), LocalTime.of(8, 0), now.zone)
            }.getOrNull()
            if (candidate != null && candidate.isAfter(now)) return candidate
            year++
        }
        return now.plusYears(1).withHour(8).withMinute(0).withSecond(0).withNano(0)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, BirthdayReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun notify(context: Context) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val person = AppRepository(context).load().preferences.name.ifBlank { "今天的你" }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.selavie.zhixing.R.drawable.ic_notification)
            .setContentTitle("生日快乐，$person！")
            .setContentText("愿新的一岁，所行皆坦途，所愿皆有回音。")
            .setStyle(Notification.BigTextStyle().bigText("生日快乐，$person！愿新的一岁，所行皆坦途，所愿皆有回音。今天也请好好照顾自己。"))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
}

class BirthdayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        BirthdayReminder.notify(context)
        BirthdayReminder.schedule(context, AppRepository(context).load().preferences.birthday)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            BirthdayReminder.schedule(context, AppRepository(context).load().preferences.birthday)
        }
    }
}
