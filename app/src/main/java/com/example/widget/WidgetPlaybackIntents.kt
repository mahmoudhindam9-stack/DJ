package com.example.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.player.MusicService

object WidgetPlaybackIntents {
    fun wireButtons(
        context: Context, 
        views: RemoteViews, 
        appWidgetId: Int, 
        prevViewId: Int, 
        playViewId: Int, 
        nextViewId: Int,
        requestCodeBase: Int = appWidgetId * 10
    ) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(prevViewId, PendingIntent.getBroadcast(context, requestCodeBase, Intent(context, WidgetActionReceiver::class.java).setAction(MusicService.ACTION_PREV), flags))
        views.setOnClickPendingIntent(playViewId, PendingIntent.getBroadcast(context, requestCodeBase + 1, Intent(context, WidgetActionReceiver::class.java).setAction(MusicService.ACTION_TOGGLE_PLAY), flags))
        views.setOnClickPendingIntent(nextViewId, PendingIntent.getBroadcast(context, requestCodeBase + 2, Intent(context, WidgetActionReceiver::class.java).setAction(MusicService.ACTION_NEXT), flags))
    }
}
