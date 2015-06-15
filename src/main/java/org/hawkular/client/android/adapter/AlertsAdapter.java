package org.hawkular.client.android.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.hawkular.client.android.R;
import org.hawkular.client.android.backend.model.Alert;
import org.hawkular.client.android.backend.model.AlertEvaluation;
import org.hawkular.client.android.backend.model.AlertType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.ButterKnife;
import butterknife.InjectView;

public final class AlertsAdapter extends BindableAdapter<Alert> {
    static final class ViewHolder {
        @InjectView(R.id.text_title)
        TextView titleText;

        @InjectView(R.id.text_message)
        TextView messageText;

        public ViewHolder(@NonNull View view) {
            ButterKnife.inject(this, view);
        }
    }

    private final List<Alert> alerts;

    public AlertsAdapter(@NonNull Context context, @NonNull List<Alert> alerts) {
        super(context);

        this.alerts = alerts;
    }

    @Override
    public Alert getItem(int position) {
        return alerts.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getCount() {
        return alerts.size();
    }

    @NonNull
    @Override
    public View newView(LayoutInflater inflater, int position, ViewGroup viewContainer) {
        View view = inflater.inflate(R.layout.layout_list_item_alert, viewContainer, false);

        view.setTag(new ViewHolder(view));

        return view;
    }

    @Override
    public void bindView(Alert alert, int position, View view) {
        ViewHolder viewHolder = (ViewHolder) view.getTag();

        viewHolder.titleText.setText(getAlertTitle(view.getContext(), alert));
        viewHolder.messageText.setText(getAlertMessage(view.getContext(), alert));
    }

    private String getAlertTitle(Context context, Alert alert) {
        return formatTimestamp(context, getAlertStartTimestamp(alert));
    }

    private String getAlertMessage(Context context, Alert alert) {
        switch (getAlertType(alert)) {
            case AVAILABILITY:
                return getAvailabilityAlertMessage(context, alert);

            case THRESHOLD:
                return getThresholdAlertMessage(context, alert);

            default:
                throw new RuntimeException("Alert is not supported");
        }
    }

    private String getAvailabilityAlertMessage(Context context, Alert alert) {
        long alertStartTimestamp = getAlertStartTimestamp(alert);
        long alertFinishTimestamp = getAlertFinishTimestamp(alert);

        return String.format("Server was down for %d seconds (until %s).",
            TimeUnit.MILLISECONDS.toSeconds(alertFinishTimestamp - alertStartTimestamp),
            formatTimestamp(context, alertFinishTimestamp));
    }

    private String getThresholdAlertMessage(Context context, Alert alert) {
        long alertStartTimestamp = getAlertStartTimestamp(alert);
        long alertFinishTimestamp = getAlertFinishTimestamp(alert);

        double alertAverage = getAlertAverageValue(alert);
        double alertThreshold = getAlertThreshold(alert);

        return String.format("Response time was above the threshold (%.1f ms) for %d seconds (until %s). The average response time was %.1f ms.",
            alertThreshold,
            TimeUnit.MILLISECONDS.toSeconds(alertFinishTimestamp - alertStartTimestamp),
            formatTimestamp(context, alertFinishTimestamp),
            alertAverage);
    }

    private String formatTimestamp(Context context, long timestamp) {
        return DateUtils.formatDateTime(context, timestamp,
            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
    }

    private AlertType getAlertType(Alert alert) {
        for (List<AlertEvaluation> alertEvaluations : alert.getEvaluations()) {
            for (AlertEvaluation alertEvaluation : alertEvaluations) {
                AlertType alertType = alertEvaluation.getCondition().getType();

                if (alertType != null) {
                    return alertType;
                }
            }
        }

        throw new RuntimeException("No alert type found");
    }

    private long getAlertStartTimestamp(Alert alert) {
        List<Long> alertStarts = new ArrayList<>();

        for (List<AlertEvaluation> alertEvaluations : alert.getEvaluations()) {
            for (AlertEvaluation alertEvaluation : alertEvaluations) {
                alertStarts.add(alertEvaluation.getDataTimestamp());
            }
        }

        return Collections.min(alertStarts);
    }

    private long getAlertFinishTimestamp(Alert alert) {
        return alert.getTimestamp();
    }

    private double getAlertAverageValue(Alert alert) {
        double alertValuesSum = 0;
        long alertValuesCount = 0;

        for (List<AlertEvaluation> alertEvaluations : alert.getEvaluations()) {
            for (AlertEvaluation alertEvaluation : alertEvaluations) {
                alertValuesSum += alertEvaluation.getValue();

                alertValuesCount++;
            }
        }

        return alertValuesSum / alertValuesCount;
    }

    private double getAlertThreshold(Alert alert) {
        for (List<AlertEvaluation> alertEvaluations : alert.getEvaluations()) {
            for (AlertEvaluation alertEvaluation : alertEvaluations) {
                double alertThreshold = alertEvaluation.getCondition().getThreshold();

                if (alertThreshold >= 0) {
                    return alertThreshold;
                }
            }
        }

        throw new RuntimeException("No alert threshold found");
    }
}
