package com.lw.top.lib_core.data.repository

import com.lw.top.lib_core.data.local.dao.TranslationDao
import com.lw.top.lib_core.data.local.entity.TranslationMessageEntity
import com.lw.top.lib_core.data.local.entity.TranslationSessionEntity
import com.lw.top.lib_core.data.local.entity.TranslationWithMessages
import com.lw.top.lib_core.data.repository.base.BaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TranslationRepository @Inject constructor(
    private val translationDao: TranslationDao
) : BaseRepository() {

    fun getAllSessionsWithMessagesFlow(): Flow<List<TranslationWithMessages>> {
        return translationDao.getAllSessionsWithMessagesFlow()
    }

    fun getSessionsWithMessagesByModeFlow(mode: String): Flow<List<TranslationWithMessages>> {
        return translationDao.getSessionsWithMessagesByModeFlow(mode)
    }

    suspend fun insertSession(session: TranslationSessionEntity) {
        withContext(Dispatchers.IO) {
            translationDao.insertSession(session)
        }
    }

    suspend fun insertMessage(message: TranslationMessageEntity) {
        withContext(Dispatchers.IO) {
            translationDao.insertMessage(message)
        }
    }

    suspend fun getMessageById(requestId: String, messageId: String): TranslationMessageEntity? {
        return withContext(Dispatchers.IO) {
            translationDao.getMessageById(requestId, messageId)
        }
    }

    suspend fun getMessageBySegmentRequestId(requestId: String): TranslationMessageEntity? {
        return withContext(Dispatchers.IO) {
            translationDao.getMessageBySegmentRequestId(requestId)
        }
    }

    suspend fun clearAllTranslations() {
        withContext(Dispatchers.IO) {
            translationDao.clearAll()
        }
    }

    suspend fun clearTranslationsByMode(mode: String) {
        withContext(Dispatchers.IO) {
            translationDao.clearByMode(mode)
        }
    }

    suspend fun deleteMessageById(requestId: String, messageId: String) {
        withContext(Dispatchers.IO) {
            translationDao.deleteMessageById(requestId, messageId)
        }
    }

    suspend fun getMessagesByRequestId(requestId: String): List<TranslationMessageEntity> {
        return withContext(Dispatchers.IO) {
            translationDao.getMessagesByRequestId(requestId)
        }
    }

    /** 实时翻译：同一 requestId 只保留一条记录，清理文本/音频 messageId 不一致的重复行。 */
    suspend fun upsertRealTimeMessage(message: TranslationMessageEntity) {
        withContext(Dispatchers.IO) {
            translationDao.deleteMessagesByRequestId(message.requestId)
            translationDao.insertMessage(message)
        }
    }
}
