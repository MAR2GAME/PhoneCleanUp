package com.cleanphone.freespace.utility.utils

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.cleanphone.freespace.utility.bean.Screenshot


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.use
import kotlin.let
import kotlin.run


class ScreenshotRepository(private val context: Context) {

    companion object {
        private const val SCREENSHOTS_DIRECTORY_PATTERN = "%Screenshots%"
        private const val SORT_ORDER_DESC = "DESC"
    }

    private var _totalSize: Long = 0
    val totalSize: Long
        get() = _totalSize

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATA
    )

    /**
     * 加载截图列表
     * @return 包含截图列表的Result对象
     */
    suspend fun loadScreenshots(): List<Screenshot> = withContext(Dispatchers.IO) {
        return@withContext try {
            val screenshots = queryScreenshots()
            screenshots
        } catch (e: Exception) {
            emptyList() // 发生异常时返回空列表
        }
    }

    /**
     * 安全地加载截图列表，返回包含成功/失败状态的结果
     */
    suspend fun loadScreenshotsWithResult(): LoadResult<List<Screenshot>> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val screenshots = queryScreenshots()
                LoadResult.Success(screenshots)
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }

    private fun queryScreenshots(): List<Screenshot> {
        val screenshots = mutableListOf<Screenshot>()
        _totalSize = 0 // 重置总大小

        val contentResolver: ContentResolver = context.contentResolver

        val selection = buildSelection()
        val selectionArgs = arrayOf(SCREENSHOTS_DIRECTORY_PATTERN)
        val sortOrder = buildSortOrder()

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            processCursor(cursor, screenshots)
        } ?: run {
        }

        return screenshots
    }

    private fun buildSelection(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Images.Media.DATA} LIKE ?"
        }
    }

    private fun buildSortOrder(): String {
        return "${MediaStore.Images.Media.DATE_TAKEN} $SORT_ORDER_DESC"
    }

    private fun processCursor(cursor: Cursor, screenshots: MutableList<Screenshot>) {
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

        while (cursor.moveToNext()) {
            try {
                processScreenshotRow(cursor, idColumn, nameColumn, dateColumn, sizeColumn, dataColumn)?.let { screenshot ->
                    screenshots.add(screenshot)
                    _totalSize += screenshot.size
                }
            } catch (e: Exception) {

            }
        }
    }

    private fun processScreenshotRow(
        cursor: Cursor,
        idColumn: Int,
        nameColumn: Int,
        dateColumn: Int,
        sizeColumn: Int,
        dataColumn: Int
    ): Screenshot? {
        return try {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn) ?: return null
            val dateTaken = cursor.getLong(dateColumn)
            val size = cursor.getLong(sizeColumn)
            val path = cursor.getString(dataColumn) ?: return null

            val contentUri = Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id.toString()
            )

            Screenshot(id, name, dateTaken, size, contentUri, path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 清除缓存数据
     */
    fun clearCache() {
        _totalSize = 0
    }

    /**
     * 自定义结果密封类，用于更清晰的状态管理
     */
    sealed class LoadResult<out T> {
        data class Success<out T>(val data: T) : LoadResult<T>()
        data class Error(val exception: Exception) : LoadResult<Nothing>()
        object Loading : LoadResult<Nothing>()
    }
}