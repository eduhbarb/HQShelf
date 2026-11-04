package com.hqshelf.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ShelfWidgetUpdater {

    fun update(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, ShelfWidgetReceiver::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        Log.d("ShelfWidgetUpdater", ">>> IDs ativas: ${ids.size}")
        if (ids.isEmpty()) {
            Log.w("ShelfWidgetUpdater", ">>> Nenhuma instância de widget encontrada")
            return
        }

        // Notifica mudança de dados
        for (id in ids) {
            try {
                appWidgetManager.notifyAppWidgetViewDataChanged(id, -1)
            } catch (e: Exception) {
                Log.e("ShelfWidgetUpdater", ">>> Erro no notify id=$id", e)
            }
        }

        // Broadcast explícito para o receiver
        try {
            val intent = Intent(context, ShelfWidgetReceiver::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
            Log.d("ShelfWidgetUpdater", ">>> Broadcast enviado para ${ids.size} id(s)")
        } catch (e: Exception) {
            Log.e("ShelfWidgetUpdater", ">>> Erro ao enviar broadcast", e)
        }

        // updateAll do Glance — re-executa provideGlance em todas as instâncias
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ShelfGlanceWidget().updateAll(context)
                Log.d("ShelfWidgetUpdater", ">>> updateAll concluído")
            } catch (e: Exception) {
                Log.e("ShelfWidgetUpdater", ">>> Erro no updateAll", e)
            }
        }
    }
}
