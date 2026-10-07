package lv.dimy.eunlwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class EunlWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        QuoteService.start(context);
    }

    @Override public void onEnabled(Context context) {
        QuoteService.start(context);
    }

    @Override public void onDisabled(Context context) {
        context.stopService(new Intent(context, QuoteService.class));
    }
}
