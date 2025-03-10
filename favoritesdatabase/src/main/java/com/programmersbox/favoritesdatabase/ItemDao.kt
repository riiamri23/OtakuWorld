package com.programmersbox.favoritesdatabase

import androidx.room.*
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFavorite(model: DbModel): Completable

    @Delete
    fun deleteFavorite(model: DbModel): Completable

    @Query("SELECT * FROM FavoriteItem")
    fun getAllFavorites(): Flowable<List<DbModel>>

    @Query("SELECT * FROM FavoriteItem")
    fun getAllFavoritesFlow(): Flow<List<DbModel>>

    @Query("SELECT * FROM FavoriteItem")
    fun getAllFavoritesSync(): List<DbModel>

    @Query("SELECT COUNT(*) FROM FavoriteItem WHERE url = :url")
    fun getItemById(url: String): Flowable<Int>

    @Query("SELECT EXISTS(SELECT * FROM FavoriteItem WHERE url=:url)")
    fun containsItem(url: String): Flowable<Boolean>

    @Query("SELECT EXISTS(SELECT * FROM FavoriteItem WHERE url=:url)")
    fun containsItemFlow(url: String): Flow<Boolean>

    @Query("SELECT * FROM FavoriteItem WHERE url = :url")
    fun getItemByUrl(url: String): Maybe<DbModel>

    @Update
    fun updateItem(model: DbModel): Completable

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertChapter(chapterWatched: ChapterWatched): Completable

    @Delete
    fun deleteChapter(chapterWatched: ChapterWatched): Completable

    @Query("SELECT * FROM ChapterWatched where favoriteUrl = :url")
    fun getAllChapters(url: String): Flowable<List<ChapterWatched>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertNotification(notificationItem: NotificationItem): Completable

    @Delete
    fun deleteNotification(notificationItem: NotificationItem): Completable

    @Query("DELETE FROM Notifications")
    fun deleteAllNotifications(): Single<Int>

    @Query("SELECT * FROM Notifications where url = :url")
    fun getNotificationItem(url: String): NotificationItem

    @Query("SELECT * FROM Notifications")
    fun getAllNotifications(): Single<List<NotificationItem>>

    @Query("SELECT * FROM Notifications")
    fun getAllNotificationsFlowable(): Flowable<List<NotificationItem>>

    @Query("SELECT COUNT(id) FROM Notifications")
    fun getAllNotificationCount(): Flowable<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM Notifications WHERE url = :url)")
    fun doesNotificationExist(url: String): Flowable<Boolean>

    @Query("SELECT * FROM Notifications")
    fun getAllNotificationsFlow(): Flow<List<NotificationItem>>

    @Query("SELECT COUNT(id) FROM Notifications")
    fun getAllNotificationCountFlow(): Flow<Int>

}