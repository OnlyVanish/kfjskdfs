package ir.pinbom.app.data

import android.content.Context
import java.io.File

/** Run before Room or preferences are opened. Never overwrite a destination. */
object StoreMigration {
    @Synchronized fun run(context: Context) {
        val root = File(context.applicationInfo.dataDir)
        fun move(old: File, new: File) {
            if (old.exists() && !new.exists()) { new.parentFile?.mkdirs(); old.renameTo(new) }
        }
        runCatching {
            for (name in listOf("session", "preferences")) {
                val shared = File(root, "shared_prefs")
                for (suffix in listOf(".xml", ".xml.bak")) move(File(shared, "bumo_$name$suffix"), File(shared, "pinbom_$name$suffix"))
                val ds = File(context.filesDir, "datastore")
                move(File(ds, "bumo_$name.preferences_pb"), File(ds, "pinbom_$name.preferences_pb"))
            }
            val oldDb = context.getDatabasePath("bumo-cache.db")
            val newDb = context.getDatabasePath("pinbom-cache.db")
            // Keep an already existing current database and all its sidecars intact.
            if (!newDb.exists()) for (suffix in listOf("-wal", "-shm", "")) move(File(oldDb.path + suffix), File(newDb.path + suffix))
        }
    }
}
