package pl.contactpro.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Birthday(val month: Int, val day: Int, val year: Int?) {

    private fun at(y: Int): LocalDate =
        if (month == 2 && day == 29 && !Year.isLeap(y.toLong())) LocalDate.of(y, 2, 28)
        else LocalDate.of(y, month, day)

    fun nextDate(today: LocalDate): LocalDate {
        val thisYear = at(today.year)
        return if (thisYear.isBefore(today)) at(today.year + 1) else thisYear
    }

    fun daysUntil(today: LocalDate): Int = ChronoUnit.DAYS.between(today, nextDate(today)).toInt()

    /** Wiek, który osoba skończy w najbliższe urodziny. */
    fun turningAge(today: LocalDate): Int? =
        year?.let { nextDate(today).year - it }?.takeIf { it in 1..130 }

    fun label(): String =
        String.format(Locale.ROOT, "%02d.%02d", day, month) + (year?.let { ".$it" } ?: "")

    fun toStorage(): String =
        if (year != null) String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
        else String.format(Locale.ROOT, "--%02d-%02d", month, day)

    companion object {
        fun parse(raw: String): Birthday? {
            val s = raw.trim()
            fun make(y: Int?, m: Int, d: Int): Birthday? {
                if (m !in 1..12 || d !in 1..31) return null
                val year = y?.takeIf { it >= 1900 }
                return runCatching { LocalDate.of(year ?: 2000, m, d) }.getOrNull()?.let { Birthday(m, d, year) }
            }
            Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(s)?.let {
                val (y, m, d) = it.destructured; return make(y.toInt(), m.toInt(), d.toInt())
            }
            Regex("""^--(\d{2})-?(\d{2})""").find(s)?.let {
                val (m, d) = it.destructured; return make(null, m.toInt(), d.toInt())
            }
            Regex("""^(\d{4})(\d{2})(\d{2})$""").find(s)?.let {
                val (y, m, d) = it.destructured; return make(y.toInt(), m.toInt(), d.toInt())
            }
            Regex("""^(\d{1,2})[./](\d{1,2})[./](\d{4})$""").find(s)?.let {
                val (d, m, y) = it.destructured; return make(y.toInt(), m.toInt(), d.toInt())
            }
            return null
        }
    }
}

fun whenText(days: Int): String = when (days) {
    0 -> "Dziś!"
    1 -> "Jutro"
    else -> "za $days dni"
}

class BirthdayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val today = LocalDate.now()
        val people = runCatching { ContactsRepository.load(ctx).contacts }.getOrDefault(emptyList())
            .filter { it.birthday?.daysUntil(today) == 0 }
        if (people.isNotEmpty()) BirthdayNotifier.notify(ctx, people, today)
        return Result.success()
    }
}

object BirthdayNotifier {
    private const val CHANNEL = "birthdays"

    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val request = PeriodicWorkRequestBuilder<BirthdayWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("birthday-reminder", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Urodziny", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Przypomnienia o urodzinach kontaktów"
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun notify(context: Context, people: List<ContactItem>, today: LocalDate) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val title = if (people.size == 1) "Dziś urodziny: ${people[0].name}" else "Dziś urodziny obchodzą ${people.size} osoby"
        val text = people.joinToString(", ") { c ->
            val age = c.birthday?.turningAge(today)
            if (age != null) "${c.name} ($age)" else c.name
        } + " – złóż życzenia!"
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, "birthdays")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cake)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1001, notification)
    }
}
