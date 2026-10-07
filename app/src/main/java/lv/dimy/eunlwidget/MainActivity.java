package lv.dimy.eunlwidget;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        TextView t = new TextView(this);
        t.setTextSize(17);
        t.setPadding(32,48,32,32);
        t.setText(
                "EUNL Widget\n\n" +
                "Обновление: примерно каждые 60 секунд.\n\n" +
                "Источник: Yahoo Finance.\n" +
                "REAL-TIME показывается только если источник " +
                "сообщает отсутствие задержки.\n" +
                "Иначе отображается DELAYED.\n\n" +
                "Для фоновой работы используется foreground service.\n\n" +
                "Нажмите на виджет для немедленного обновления."
        );
        setContentView(t);

        QuoteService.start(this);
    }
}
