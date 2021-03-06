package de.tum.`in`.tca.ticketcheck.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import de.tum.`in`.tca.ticketcheck.component.ui.chat.model.ChatMember
import de.tum.`in`.tca.ticketcheck.utils.DateTimeUtils
import org.joda.time.DateTime

class Converters {
    @TypeConverter
    fun isoToDateTime(str: String?): DateTime? {
        return if (str == null) null else DateTimeUtils.getDateTime(str)
    }

    @TypeConverter
    fun fromDateTime(date: DateTime?): String? {
        return if (date == null) null else DateTimeUtils.getDateTimeString(date)
    }

    @TypeConverter
    fun fromMember(member: ChatMember): String = Gson().toJson(member)

    @TypeConverter
    fun toMember(member: String): ChatMember = Gson().fromJson<ChatMember>(member, ChatMember::class.java)
}
