package com.lw.top.lib_core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lw.top.lib_core.data.local.entity.TranslationMessageEntity
import com.lw.top.lib_core.data.local.entity.TranslationSessionEntity
import com.lw.top.lib_core.data.local.entity.TranslationWithMessages
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: TranslationSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: TranslationMessageEntity)

    @Transaction
    @Query("SELECT * FROM translation_sessions ORDER BY timestamp DESC")
    fun getAllSessionsWithMessagesFlow(): Flow<List<TranslationWithMessages>>

    @Transaction
    @Query(
        "SELECT * FROM translation_sessions WHERE translationMode = :mode ORDER BY timestamp DESC",
    )
    fun getSessionsWithMessagesByModeFlow(mode: String): Flow<List<TranslationWithMessages>>

    @Transaction
    @Query("SELECT * FROM translation_sessions WHERE requestId = :requestId LIMIT 1")
    suspend fun getSessionWithMessages(requestId: String): TranslationWithMessages?

    @Query("SELECT * FROM translation_messages WHERE requestId = :requestId AND messageId = :messageId LIMIT 1")
    suspend fun getMessageById(requestId: String, messageId: String): TranslationMessageEntity?

    /** 实时翻译：同一句话的文本与音频 requestId 相同、messageId 可能不同，按 requestId 查找已有片段。 */
    @Query("SELECT * FROM translation_messages WHERE requestId = :requestId ORDER BY timestamp ASC LIMIT 1")
    suspend fun getMessageBySegmentRequestId(requestId: String): TranslationMessageEntity?

    @Query("DELETE FROM translation_sessions")
    suspend fun clearAll()

    @Query("DELETE FROM translation_sessions WHERE translationMode = :mode")
    suspend fun clearByMode(mode: String)

    @Query("DELETE FROM translation_messages WHERE requestId = :requestId AND messageId = :messageId")
    suspend fun deleteMessageById(requestId: String, messageId: String)

    /** 实时翻译：同一 requestId 可能有多条 messageId 不同的记录。 */
    @Query("SELECT * FROM translation_messages WHERE requestId = :requestId ORDER BY timestamp ASC")
    suspend fun getMessagesByRequestId(requestId: String): List<TranslationMessageEntity>

    @Query("DELETE FROM translation_messages WHERE requestId = :requestId")
    suspend fun deleteMessagesByRequestId(requestId: String)
}
