package com.movesense.samples.ecgsample.helpers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.movesense.samples.ecgsample.layout.CustomMarkerView;
import com.movesense.samples.ecgsample.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GraphPainter {
    public Period currentPeriod;
    public String beforeText, AfterText;

    public GraphPainter(String beforeText, String AfterText) {
        this.beforeText = beforeText;
        this.AfterText = AfterText;
    }

    public enum Period {
        DAY, WEEK, MONTH
    }

    public long[] getTimestampsForPeriod(Period period) {
        Calendar calendar = Calendar.getInstance();
        long endTs = calendar.getTimeInMillis();

        switch (period) {
            case DAY:
                calendar.add(Calendar.DAY_OF_YEAR, -1);
                break;
            case WEEK:
                calendar.add(Calendar.WEEK_OF_YEAR, -1);
                break;
            case MONTH:
                calendar.add(Calendar.MONTH, -1);
                break;
        }

        long startTs = calendar.getTimeInMillis();
        return new long[]{startTs, endTs};
    }

    public void updateGraph(Context context, LineChart graphHistory, JSONArray temperatureData,
                            long startTs, long endTs) throws JSONException {
        List<Entry> entries = new ArrayList<>();
        long interval = getInterval();

        // Parse JSON data into a list of timestamp-value pairs
        List<Long> timestamps = new ArrayList<>();
        List<Float> values = new ArrayList<>();

        for (int i = 0; i < temperatureData.length(); i++) {
            JSONObject obj = temperatureData.getJSONObject(i);
            long timestamp = obj.getLong("ts");
            float value = Float.parseFloat(obj.getString("value"));
            timestamps.add(timestamp);
            values.add(value);
        }

        // Fill entries by averaging data points for each interval
        for (long time = startTs; time <= endTs; time += interval) {
            float averageValue = calculateAverageInInterval(time, time + interval, timestamps, values);
            if (!Float.isNaN(averageValue)) {
                entries.add(new Entry(time, averageValue));  // Only add valid entries
            }
        }

        // Check if entries list has data
        if (entries.isEmpty()) {
            graphHistory.clear(); // Limpia el gráfico para mostrarlo vacío
            graphHistory.setNoDataText("No data available for the selected period.");
            graphHistory.invalidate();
            return; // Handle no data case if needed
        }

        // Configure the dataset for the chart
        LineDataSet dataSet = new LineDataSet(entries, "Temperature");
        dataSet.setDrawValues(false); // Disable values on data points
        dataSet.setDrawCircles(true); // Draw circles on data points
        dataSet.setCircleRadius(3f);
        dataSet.setColor(Color.BLUE); // Color of the line
        dataSet.setCircleColor(Color.RED); // Color of data points
        dataSet.setLineWidth(2f); // Line thickness

        // Set up LineData and attach it to the chart
        CustomMarkerView mv = new CustomMarkerView(context, R.layout.marker_view, beforeText, AfterText);
        LineData lineData = new LineData(dataSet);
        graphHistory.setData(lineData);
        graphHistory.setMarker(mv);

        //Set up minimum and maximum
        graphHistory.getXAxis().setAxisMinimum(startTs);
        graphHistory.getXAxis().setAxisMaximum(endTs);

        // Customize X-Axis to display dates instead of timestamps
        graphHistory.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        graphHistory.getXAxis().setGranularity(1f);
        graphHistory.getAxisRight().setEnabled(false);
        graphHistory.getDescription().setEnabled(false);
        graphHistory.getXAxis().setValueFormatter(new ValueFormatter() {
            @SuppressLint("SimpleDateFormat")
            @Override
            public String getFormattedValue(float value) {
                if (currentPeriod == Period.DAY) {
                    return new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date((long) value));
                }
                else {
                    return new java.text.SimpleDateFormat("dd/MM").format(new java.util.Date((long) value));
                }
            }
        });

        // Refresh the chart
        graphHistory.invalidate();
    }

    private long getInterval() {
        long interval = 0;

        switch (currentPeriod) {
            case DAY:
                interval = TimeUnit.MINUTES.toMillis(10);
                break;
            case WEEK:
                interval = TimeUnit.HOURS.toMillis(1);
                break;
            case MONTH:
                interval = TimeUnit.HOURS.toMillis(6);
                break;
        }

        return interval;
    }

    // Helper function to calculate the average of values within a specific time interval
    private float calculateAverageInInterval(long intervalStart, long intervalEnd, List<Long> timestamps, List<Float> values) {
        float sum = 0;
        int count = 0;

        for (int i = 0; i < timestamps.size(); i++) {
            long timestamp = timestamps.get(i);
            if (timestamp >= intervalStart && timestamp < intervalEnd) {
                sum += values.get(i);
                count++;
            }
        }

        // Return average if there are values in the interval, otherwise NaN to create a gap
        return count > 0 ? sum / count : Float.NaN;
    }

    public Period getCurrentPeriod() {
        return currentPeriod;
    }

    public void setCurrentPeriod(Period currentPeriod) {
        this.currentPeriod = currentPeriod;
    }
}
