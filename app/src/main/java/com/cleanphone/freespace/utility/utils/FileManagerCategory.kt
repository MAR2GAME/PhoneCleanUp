package com.cleanphone.freespace.utility.utils

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.cleanphone.freespace.utility.R


enum class FileManagerCategory(
    @StringRes val routeTagRes: Int,
    @StringRes val displayNameRes: Int,
    @StringRes val loadingHintRes: Int,
    @DrawableRes val placeholderIconRes: Int,
    val supportsTypeFilter: Boolean
) {
    LARGE_FILES(
        routeTagRes = R.string.large_file_cleaner,
        displayNameRes = R.string.large_files,
        loadingHintRes = R.string.scanning_large_file,
        placeholderIconRes = R.mipmap.ic_file ,
        supportsTypeFilter = true
    ),
    IMAGES(
        routeTagRes = R.string.images,
        displayNameRes = R.string.images,
        loadingHintRes = R.string.scanning_images,
        placeholderIconRes = R.mipmap.ic_image,
        supportsTypeFilter = false
    ),
    VIDEO(
        routeTagRes = R.string.video,
        displayNameRes = R.string.video,
        loadingHintRes = R.string.scanning_videos,
        placeholderIconRes = R.mipmap.ic_video,
        supportsTypeFilter = false
    ),
    AUDIO(
        routeTagRes = R.string.audio,
        displayNameRes = R.string.audio,
        loadingHintRes = R.string.scanning_audio_files,
        placeholderIconRes = R.mipmap.ic_audio,
        supportsTypeFilter = false
    ),
    DOCUMENTS(
        routeTagRes = R.string.documents,
        displayNameRes = R.string.documents,
        loadingHintRes = R.string.scanning_documents,
        placeholderIconRes = R.mipmap.main_ic_documents,
        supportsTypeFilter = false
    ),
    APK(
        routeTagRes = R.string.apk,
        displayNameRes = R.string.apk,
        loadingHintRes = R.string.scanning_apk_files,
        placeholderIconRes = R.mipmap.ic_apk,
        supportsTypeFilter = false
    ),
    DOWNLOAD(
        routeTagRes = R.string.download,
        displayNameRes = R.string.download,
        loadingHintRes = R.string.scanning_downloads,
        placeholderIconRes = R.mipmap.main_ic_download,
        supportsTypeFilter = false
    );

    companion object {
        fun fromTag(context: Context, tag: String?): FileManagerCategory {
            return values().firstOrNull { context.getString(it.routeTagRes) == tag } ?: LARGE_FILES
        }

        fun isManagedTag(context: Context, tag: String?): Boolean {
            return values().any { context.getString(it.routeTagRes) == tag }
        }
    }
}
