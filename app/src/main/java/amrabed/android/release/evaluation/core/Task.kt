package amrabed.android.release.evaluation.core

import amrabed.android.release.evaluation.R
import amrabed.android.release.evaluation.utilities.preferences.Preferences
import android.content.Context
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import org.joda.time.*
import org.joda.time.chrono.IslamicChronology
import java.util.*

@Entity(tableName = "history", primaryKeys = ["date", "task"])
data class Record(var date: Long, var task: String, var selection: Byte, var note: String? = null)

@Entity(tableName = "tasks")
@Parcelize
data class Task(
        var title: String? = null,
        var reminder: String? = null,
        @ColumnInfo(name = "currentIndex") var index: Int = 0,
        @ColumnInfo(defaultValue = "-1") var defaultIndex: Int = -1, /* Original index of an item of the default list */
        @ColumnInfo(defaultValue = "0x7f") var activeDays: BooleanArray = activeDays(defaultIndex),
        @PrimaryKey var id: String = UUID.randomUUID().toString(),
        @Ignore val history: HashMap<Long, Byte> = hashMapOf()) : Parcelable {

    @IgnoredOnParcel
    @Ignore
    private val guideEntry: Int = if (defaultIndex == -1) 0 else DEFAULT_LIST[defaultIndex]

    fun getTitle(context: Context): String? {
        if (isControlledBySettings()) title = null
        // Unless title is manually set by the user, return default title
        return title ?: Preferences.getDefaultTaskTitles(context)[defaultIndex]
    }

    /**
     * Get shifted version of active days for Arabic list of days
     * (Mon, Tue, ..., Fri) -> (Sat, Sun, ..., Fri)
     *
     * @return shifted version of active days
     */
    fun getActiveDays(context: Context): BooleanArray {
        val shift = context.resources.getInteger(R.integer.dayShift)
        if (shift == 0) return activeDays
        val shifted = BooleanArray(7)
        for (i in activeDays.indices) {
            shifted[(i + shift) % 7] = activeDays[i]
        }
        return shifted
    }

    fun setActiveDay(day: Int, isActive: Boolean) {
        activeDays[day - 1] = isActive
    }

    /**
     * Set next reminder time based on active days
     *
     * @param reminderTime LocalTime (hour and minute) of reminder
     * @return task with next reminder time set (null for no active days)
     */
    fun nextReminder(reminderTime: LocalTime): Task {
        val now = LocalDateTime()
        val reminderDateTime = now.withTime(reminderTime.hourOfDay, reminderTime.minuteOfHour, 0, 0)
        if (isActiveDay(now.dayOfWeek) && now.isBefore(reminderDateTime)) {
            // Next reminder is today
            this.reminder = reminderDateTime.toString()
        } else {
            var nextReminderDay = activeDays.withIndex().indexOfFirst { v -> v.index > now.dayOfWeek - 1 && v.value } + 1
            if (nextReminderDay != -1) {
                // Next reminder is within current week
                this.reminder = reminderDateTime.withDayOfWeek(nextReminderDay).toString()
            } else {
                nextReminderDay = activeDays.indexOfFirst { it } + 1
                // Next reminder is next week
                this.reminder = if (nextReminderDay != -1) reminderDateTime.plusWeeks(1).withDayOfWeek(nextReminderDay).toString() else null
            }
        }
        return this
    }

    fun hide() = activeDays.fill(false)

    fun isHidden() = !activeDays.reduce { a, b -> a or b }

    fun isVisible(context: Context, date: Long) = if (isFastingTask()) isFastingDay(context, date) else isActiveDay(LocalDate(date).dayOfWeek)

    fun isControlledBySettings() = isFastingTask() || isPrayerTask()

    private fun isFastingTask() = defaultIndex != -1 && DEFAULT_LIST[defaultIndex] == R.raw.fasting

    private fun isPrayerTask() = defaultIndex != -1 && DEFAULT_LIST[defaultIndex] in intArrayOf(R.raw.fajr, R.raw.cong, R.raw.isha)

    private fun isActiveDay(day: Int) = activeDays[day - 1]

    private fun isFastingDay(context: Context?, date: Long): Boolean {
        val dateHijri = DateTime(date).withChronology(IslamicChronology.getInstance())
        val month = dateHijri.monthOfYear().get()
        val dayOfMonth = dateHijri.dayOfMonth().get()
        if (month == 9) {
            // No voluntary fasting during Ramadan
            return false
        }
        if (month == 1 && (dayOfMonth == 9 || dayOfMonth == 10) || // Ashoraa
                (month == 12 && dayOfMonth == 9)) // Arafat
        {
            return true
        }

        val fasting = Preferences.getFastingDays(context)
        val dayAfterDay = fasting and 0x08 != 0
        if (dayAfterDay) {
            val lastDayOfFasting = Preferences.getLastDayOfFasting(context)
            val start = DateTime(lastDayOfFasting)
            val end = DateTime(date)
            val isMoreThanOne = Days.daysBetween(start, end).isGreaterThan(Days.ONE)
            if (isMoreThanOne) {
                Preferences.setLastDayOfFasting(context, date)
                return true
            }
        } else {
            Preferences.removeLastDayOfFasting(context)
        }

        val dayOfWeek = dateHijri.dayOfWeek().get()
        val isFastingMonday = fasting and 0x01 != 0
        val isFastingThursday = fasting and 0x02 != 0
        val isFastingWhiteDays = fasting and 0x04 != 0
        return isFastingThursday && dayOfWeek == DateTimeConstants.THURSDAY ||
                isFastingMonday && dayOfWeek == DateTimeConstants.MONDAY ||
                isFastingWhiteDays && (dayOfMonth == 13 || dayOfMonth == 14 || dayOfMonth == 15)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Task

        if (id != other.id) return false
        if (defaultIndex != other.defaultIndex) return false
        if (index != other.index) return false
        if (guideEntry != other.guideEntry) return false
        if (title != other.title) return false
        if (!activeDays.contentEquals(other.activeDays)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + defaultIndex
        result = 31 * result + index
        result = 31 * result + guideEntry
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + activeDays.contentHashCode()
        return result
    }

    companion object {
        val DEFAULT_LIST = intArrayOf(R.raw.wakeup, R.raw.brush, R.raw.night, R.raw.fasting,
                R.raw.sunna, R.raw.fajr, R.raw.fajr_azkar,
                R.raw.quran, R.raw.memorize,
                R.raw.morning, R.raw.duha,
                R.raw.sports, R.raw.friday, R.raw.work,
                R.raw.cong, R.raw.prayer_azkar, R.raw.rawateb,
                R.raw.cong, R.raw.prayer_azkar, R.raw.evening,
                R.raw.cong, R.raw.fajr_azkar, R.raw.rawateb,
                R.raw.isha, R.raw.prayer_azkar, R.raw.rawateb, R.raw.wetr,
                R.raw.diet, R.raw.manners, R.raw.honesty, R.raw.backbiting, R.raw.gaze,
                R.raw.wudu, R.raw.sleep)

        private fun activeDays(defaultIndex: Int): BooleanArray {
            if (defaultIndex != -1 && DEFAULT_LIST[defaultIndex] == R.raw.friday) {
                return BooleanArray(7) { false }.also { it[DateTimeConstants.FRIDAY - 1] = true }
            }
            if (defaultIndex != -1 && DEFAULT_LIST[defaultIndex] == R.raw.fasting) {
                return BooleanArray(7) { false }.also {
                    it[DateTimeConstants.MONDAY - 1] = true
                    it[DateTimeConstants.THURSDAY - 1] = true
                }
            }
            return BooleanArray(7) { true }
        }
    }
}