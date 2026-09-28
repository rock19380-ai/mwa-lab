package dev.mwalab.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.mwalab.storage.protocol.ProtocolEventDao
import dev.mwalab.storage.protocol.ProtocolEventEntity
import dev.mwalab.storage.session.SessionDao
import dev.mwalab.storage.session.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        ProtocolEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MwaLabDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun protocolEventDao(): ProtocolEventDao

    companion object {
        const val DATABASE_NAME = "mwa_lab.db"

        fun create(context: Context): MwaLabDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MwaLabDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
