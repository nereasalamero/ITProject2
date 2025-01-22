package com.movesense.samples.docsense.layout;

import android.content.Context;
import android.widget.TextView;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import com.movesense.samples.docsense.R;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CustomMarkerView extends MarkerView {

    private TextView timeText;
    private TextView valueText;
    private String beforeText, afterText;

    public CustomMarkerView(Context context, int layoutResource, String beforeText, String afterText) {
        super(context, layoutResource);
        timeText = findViewById(R.id.time_text);
        valueText = findViewById(R.id.value_text);
        this.beforeText = beforeText;
        this.afterText = afterText;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        long timestamp = (long) e.getX(); // El timestamp está almacenado en el eje X de la entrada
        String time = new SimpleDateFormat("HH:mm").format(new Date(timestamp));

        String textTime = "Time: " + time;
        String textTemp = beforeText + e.getY() + afterText;
        timeText.setText(textTime);
        valueText.setText(textTemp);

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // Move the marker to the right and add space above the point
        float offsetX = -(getWidth()) - 40f;  // Add extra space to the right (20f as example)
        float offsetY = -(getHeight()) - 40f;  // Add extra space above the point (20f as example)
        return new MPPointF(offsetX, offsetY);
    }
}

