package ir.pinbom.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pins")
data class PinEntity(@PrimaryKey val id: Long, val title: String, val description: String,
    val width: Int, val height: Int, val createdAt: String, val imageUrl: String,
    val authorName: String, val authorAvatar: String)

@Dao
interface PinDao {
    @Query("SELECT * FROM pins ORDER BY createdAt DESC, id DESC LIMIT 300")
    fun observe(): Flow<List<PinEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<PinEntity>)
    @Query("DELETE FROM pins") suspend fun clear()
    @Query("DELETE FROM pins WHERE id NOT IN (SELECT id FROM pins ORDER BY createdAt DESC, id DESC LIMIT 300)")
    suspend fun trim()
}
@Database(entities = [PinEntity::class], version = 1, exportSchema = false)
abstract class LocalDb : RoomDatabase() { abstract fun pins(): PinDao }
