package com.programmersbox.uiviews

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.recyclerview.widget.RecyclerView
import com.programmersbox.models.ApiService
import com.programmersbox.models.ChapterModel
import com.programmersbox.sharedutils.AppUpdate

interface GenericInfo {

    val apkString: AppUpdate.AppUpdates.() -> String?

    fun createAdapter(context: Context, baseListFragment: BaseListFragment): ItemListAdapter<RecyclerView.ViewHolder>
    fun createLayoutManager(context: Context): RecyclerView.LayoutManager
    fun chapterOnClick(model: ChapterModel, allChapters: List<ChapterModel>, context: Context)
    fun sourceList(): List<ApiService>
    fun searchList(): List<ApiService> = sourceList()
    fun toSource(s: String): ApiService?
    fun customPreferences(preferenceScreen: SettingsDsl) = Unit
    fun downloadChapter(chapterModel: ChapterModel, title: String)

    @Composable
    fun ComposeShimmerItem()

}