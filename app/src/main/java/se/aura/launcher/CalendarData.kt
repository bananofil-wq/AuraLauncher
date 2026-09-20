package se.aura.launcher

import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AuraEvent(
    val title: String,
    val beginMillis: Long,
    val location: String?
)

suspend fun loadUpcomingEvents(context: Context): List<AuraEvent> = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val end = now + 7L * 24L * 60L * 60L * 1000L
    val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.EVENT_LOCATION
    )

    val events = mutableListOf<AuraEvent>()

    runCatching {
        CalendarContract.Instances.query(
            context.contentResolver,
            projection,
            now,
            end
        )?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)

            while (cursor.moveToNext() && events.size < 4) {
                val title = cursor.getString(titleIndex)?.trim().orEmpty()
                if (title.isNotEmpty()) {
                    events += AuraEvent(
                        title = title,
                        beginMillis = cursor.getLong(beginIndex),
                        location = cursor.getString(locationIndex)
                    )
                }
            }
        }
    }

    events
}
