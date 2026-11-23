package com.example.iqmemo.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.iqmemo.R
import com.example.iqmemo.data.database.NoteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MemoWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                val dao = NoteDatabase.getInstance(context).noteDao()
                // 按 updatedAt 升序（时间顺序，最早的在前）
                val notes = dao.getAllNotes().first()
                    .filter { it.content.isNotBlank() }
                    .sortedBy { it.updatedAt }

                val text = if (notes.isEmpty()) {
                    context.getString(R.string.widget_empty)
                } else {
                    notes.joinToString("\n") {
                        "• ${it.content.trim().replace("\n", " ")}"
                    }
                }

                val views = RemoteViews(context.packageName, R.layout.widget_memo)
                views.setTextViewText(R.id.widget_text, text)

                // 点击小组件打开 App
                val launchIntent = com.example.iqmemo.MainActivity.widgetIntent(context)

                if (launchIntent != null) {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        0,
                        launchIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                                android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
