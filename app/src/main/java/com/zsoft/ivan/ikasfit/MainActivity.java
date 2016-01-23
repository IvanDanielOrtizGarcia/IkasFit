package com.zsoft.ivan.ikasfit;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataDeleteRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.text.DateFormat.getDateTimeInstance;


public class MainActivity extends AppCompatActivity {
    public static final String TAG = "IkasFit";
    private static final int REQUEST_OAUTH = 1;
    private static final String DATE_FORMAT = "yyyy.MM.dd HH:mm:ss";
    private boolean connected_googlefit = false;
    private boolean googlefit_data_loaded = false;
    private boolean connected_parse = false;
    private boolean data_deleted = false;
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    private GoogleApiClient mClient = null;
    private int[] pasos;
    private float[] distancias;
    int year_step, month_step, week_step, day_step, average_step;
    float year_distance, month_distance, week_distance, day_distance, average_distance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeLogging();
        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }
        AccountManager manager = (AccountManager) getSystemService(ACCOUNT_SERVICE);
        Account[] list = manager.getAccounts();
        String gmail = null;
        for (Account account : list) {
            if (account.type.equalsIgnoreCase("com.google")) {
                gmail = account.name;
                break;
            }
        }
        ActionBar actionBar = getSupportActionBar();
        actionBar.setLogo(R.mipmap.ic_launcher);
        actionBar.setDisplayUseLogoEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        TextView textView = (TextView) findViewById(R.id.title_text_view);
        textView.setText(gmail);
        LogView logView = (LogView) findViewById(R.id.sample_logview);
        logView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final ScrollView scrollView = (ScrollView) findViewById(R.id.log_scroll);
                scrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollView.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    private void buildFitnessClient() {
        mClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ_WRITE))
                .addConnectionCallbacks(
                        new ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected to Google Fit!!!");
                                connected_googlefit = true;
                                new DataRequester().execute();
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                connected_googlefit = false;
                                empty_fields();
                                if (i == ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .enableAutoManage(this, 0, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        empty_fields();
                        Log.i(TAG, "Google Play services connection failed. Cause: " +
                                result.toString());
                        Snackbar.make(
                                MainActivity.this.findViewById(R.id.main_activity_view),
                                "Exception while connecting to Google Play services: " +
                                        result.getErrorMessage(),
                                Snackbar.LENGTH_INDEFINITE).show();
                    }
                })
                .build();
    }

    private class DataInserter extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            DataSet dataSet = insertFitnessData_steps();
            Log.i(TAG, "Inserting the dataset of steps in the History API");
            com.google.android.gms.common.api.Status insertStatus =
                    Fitness.HistoryApi.insertData(mClient, dataSet)
                            .await(1, TimeUnit.MINUTES);
            if (!insertStatus.isSuccess()) {
                Log.i(TAG, "There was a problem inserting the dataset of steps.");
                return null;
            }
            Log.i(TAG, "Steps data insert was successful!");
            dataSet = insertFitnessData_distance();
            Log.i(TAG, "Inserting the dataset of distance in the History API");
            insertStatus = Fitness.HistoryApi.insertData(mClient, dataSet)
                    .await(1, TimeUnit.MINUTES);
            if (!insertStatus.isSuccess()) {
                Log.i(TAG, "There was a problem inserting the dataset of distance.");
                return null;
            }
            Log.i(TAG, "Distance data insert was successful!");
            return null;
        }
    }

    private DataSet insertFitnessData_steps() {
        Log.i(TAG, "Creating a new data insert request for steps");
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(this)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setName(TAG + " - step count")
                .setType(DataSource.TYPE_RAW)
                .build();
        DataSet dataSet = DataSet.create(dataSource);
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        int days = cal.get(Calendar.DAY_OF_YEAR);
        Log.i(TAG, "Days in this year:" + String.valueOf(days));
        cal.set(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        for (int i = 1; i <= days; i++) {
            long startTime = cal.getTimeInMillis();
            if (i == days) {
                cal.setTime(now);
            } else {
                cal.add(Calendar.DATE, 1);
                cal.add(Calendar.SECOND, -1);
            }
            long endTime = cal.getTimeInMillis();
            cal.add(Calendar.SECOND, +1);
            Random rand = new Random();
            int randomSteps = rand.nextInt((10000 - 100) + 1) + 100;
            DateFormat dateFormat = getDateTimeInstance();
            Log.i(TAG, "Insert from " + dateFormat.format(startTime) + " to " + dateFormat.format(endTime) + " - " + randomSteps + " steps");
            DataPoint dataPoint = dataSet.createDataPoint()
                    .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS);
            dataPoint.getValue(Field.FIELD_STEPS).setInt(randomSteps);
            dataSet.add(dataPoint);
        }
        return dataSet;
    }

    private DataSet insertFitnessData_distance() {
        Log.i(TAG, "Creating a new data insert request for distance");
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(this)
                .setDataType(DataType.TYPE_DISTANCE_DELTA)
                .setName(TAG + " - distance count")
                .setType(DataSource.TYPE_RAW)
                .build();
        DataSet dataSet = DataSet.create(dataSource);
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        int days = cal.get(Calendar.DAY_OF_YEAR);
        Log.i(TAG, "Days in this year:" + String.valueOf(days));
        cal.set(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        for (int i = 1; i <= days; i++) {
            long startTime = cal.getTimeInMillis();
            if (i == days) {
                cal.setTime(now);
            } else {
                cal.add(Calendar.DATE, 1);
                cal.add(Calendar.SECOND, -1);
            }
            long endTime = cal.getTimeInMillis();
            cal.add(Calendar.SECOND, +1);
            Random rand = new Random();
            float randomDistance = rand.nextInt((40000 - 1000) + 1) + 1000;
            DateFormat dateFormat = getDateTimeInstance();
            Log.i(TAG, "Insert from " + dateFormat.format(startTime) + " to " + dateFormat.format(endTime) + " - " + randomDistance + " meters");
            DataPoint dataPoint = dataSet.createDataPoint()
                    .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS);
            dataPoint.getValue(Field.FIELD_DISTANCE).setFloat(randomDistance);
            dataSet.add(dataPoint);
        }
        return dataSet;
    }

    private class DataRequester extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            DataReadRequest readRequest = queryFitnessData();
            DataReadResult dataReadResult =
                    Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);
            printData(dataReadResult);
            return null;
        }
    }

    private DataReadRequest queryFitnessData() {
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.set(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long startTime = cal.getTimeInMillis();
        DateFormat dateFormat = getDateTimeInstance();
        Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
        Log.i(TAG, "Range End: " + dateFormat.format(endTime));
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();
        return readRequest;
    }

    private void printData(DataReadResult dataReadResult) {
        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Number of returned buckets of DataSets is: "
                    + dataReadResult.getBuckets().size());
            pasos = new int[dataReadResult.getBuckets().size()];
            distancias = new float[dataReadResult.getBuckets().size()];
            int x = 0;
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    dumpDataSet(dataSet, x);
                }
                x++;
            }
            fill_fields();
        }
    }

    private void dumpDataSet(DataSet dataSet, int index) {
        DateFormat dateFormat = getDateTimeInstance();
        int paso = 0;
        float distancia = 0;
        for (DataPoint dp : dataSet.getDataPoints()) {
            Log.i(TAG, "Data point:");
            Log.i(TAG, "\tType: " + dp.getDataType().getName());
            Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            if (dp.getDataType().getName().equalsIgnoreCase(DataType.TYPE_STEP_COUNT_DELTA.getName())) {
                for (Field field : dp.getDataType().getFields()) {
                    Log.i(TAG, "\tField: " + field.getName() +
                            " Value: " + dp.getValue(field));
                    paso += dp.getValue(field).asInt();
                }
                pasos[index] = paso;
            } else if (dp.getDataType().getName().equalsIgnoreCase(DataType.TYPE_DISTANCE_DELTA.getName())) {
                for (Field field : dp.getDataType().getFields()) {
                    Log.i(TAG, "\tField: " + field.getName() +
                            " Value: " + dp.getValue(field));
                    distancia += dp.getValue(field).asFloat();
                }
                distancias[index] = distancia;
            }
        }
    }

    private int dumpDataSet_steps(DataSet dataSet) {
        Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
        DateFormat dateFormat = getDateTimeInstance();
        int value = 0;
        for (DataPoint dp : dataSet.getDataPoints()) {
            if (dp.getDataType().getName().equalsIgnoreCase(DataType.TYPE_STEP_COUNT_DELTA.getName())) {
                Log.i(TAG, "Data point:");
                Log.i(TAG, "\tType: " + dp.getDataType().getName());
                Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                for (Field field : dp.getDataType().getFields()) {
                    Log.i(TAG, "\tField: " + field.getName() +
                            " Value: " + dp.getValue(field));
                    value += dp.getValue(field).asInt();
                }
            } else {
                value = -1;
            }
        }
        return value;
    }

    private float dumpDataSet_distance(DataSet dataSet) {
        Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
        DateFormat dateFormat = getDateTimeInstance();
        float value = 0;
        for (DataPoint dp : dataSet.getDataPoints()) {
            if (dp.getDataType().getName().equalsIgnoreCase(DataType.TYPE_DISTANCE_DELTA.getName())) {
                Log.i(TAG, "Data point:");
                Log.i(TAG, "\tType: " + dp.getDataType().getName());
                Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                for (Field field : dp.getDataType().getFields()) {
                    Log.i(TAG, "\tField: " + field.getName() +
                            " Value: " + dp.getValue(field));
                    value += dp.getValue(field).asFloat();
                }
            } else {
                value = -1;
            }
        }
        return value;
    }

    private void deleteData() {
        Log.i(TAG, "Deleting last year's step and distance count data");
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.set(Calendar.DAY_OF_YEAR, 1);
        long startTime = cal.getTimeInMillis();
        DataDeleteRequest request = new DataDeleteRequest.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .addDataType(DataType.TYPE_DISTANCE_DELTA)
                .build();

        Fitness.HistoryApi.deleteData(mClient, request)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Successfully deleted last year's step and distance count data");
                        } else {
                            Log.i(TAG, "Failed to delete last year's step and distance count data");
                        }
                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_delete_data) {
            if (connected_googlefit) {
                AlertDialog.Builder adb = new AlertDialog.Builder(this);
                adb.setTitle("¡PELIGRO!");
                adb.setMessage("¿Estás seguro de borrar de Google Fit para el año en curso?");
                adb.setIcon(android.R.drawable.ic_dialog_alert);
                adb.setPositiveButton("BORRAR", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        deleteData();
                        data_deleted = true;
                        empty_fields();
                    }
                });
                adb.setNegativeButton("CANCELAR", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                adb.show();
            } else {
                Toast.makeText(this, "¡No estás conectado a Google Fit!",
                        Toast.LENGTH_LONG).show();
            }
            return true;
        } else if (id == R.id.action_insert_random_data) {
            if (connected_googlefit) {
                if (data_deleted) {
                    new DataInserter().execute();
                    data_deleted = false;
                    empty_fields();
                } else {
                    Toast.makeText(this, "¡Borra los datos antes de poder insertar nuevos!",
                            Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "¡No estás conectado a Google Fit!",
                        Toast.LENGTH_LONG).show();
            }
            return true;
        } else if (id == R.id.action_googlefit) {
            if (!connected_googlefit) {
                buildFitnessClient();
            } else {
                new DataRequester().execute();
                return true;
            }
        } else if (id == R.id.action_parse) {
            if (!connected_googlefit) {
                Toast.makeText(this, "¡Conecta primero con Google Fit!",
                        Toast.LENGTH_LONG).show();
            } else if (!googlefit_data_loaded) {
                Toast.makeText(this, "¡Carga primero los datos de Google Fit!",
                        Toast.LENGTH_LONG).show();
            } else {

            }
        } else if (id == R.id.action_about) {
            AlertDialog.Builder adb = new AlertDialog.Builder(this);
            adb.setTitle("Acerca de");
            adb.setMessage("IKasFit v1.0\nProyecto fin de curso\nDesarrollo de Aplicaciones Multiplataforma\nEGIBIDE\nmotxuelodon@gmail.com");
            adb.setIcon(android.R.drawable.ic_dialog_info);
            adb.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            adb.show();
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeLogging() {
        LogWrapper logWrapper = new LogWrapper();
        Log.setLogNode(logWrapper);
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);
        LogView logView = (LogView) findViewById(R.id.sample_logview);
        logView.setTextAppearance(this, R.style.Log);
        msgFilter.setNext(logView);
        Log.i(TAG, "Ready");
    }

    private void empty_fields() {
        RelativeLayout myRelativeLayout = (RelativeLayout) findViewById(R.id.main_activity_view);
        for (int i = 0; i < myRelativeLayout.getChildCount(); i++)
            if (myRelativeLayout.getChildAt(i) instanceof TextView) {
                int id = myRelativeLayout.getChildAt(i).getId();
                if (id == R.id.textView_year_value_step ||
                        id == R.id.textView_month_value_step ||
                        id == R.id.textView_week_value_step ||
                        id == R.id.textView_day_value_step ||
                        id == R.id.textView_average_value_step ||
                        id == R.id.textView_year_value_distance ||
                        id == R.id.textView_month_value_distance ||
                        id == R.id.textView_week_value_distance ||
                        id == R.id.textView_day_value_distance ||
                        id == R.id.textView_average_value_distance ||
                        id == R.id.textView_ranking_value ||
                        id == R.id.textView_number1_value ||
                        id == R.id.textView_total_value) {
                    TextView textView = (TextView) myRelativeLayout.getChildAt(i);
                    textView.setText(R.string.unknown);
                }
            }
        googlefit_data_loaded = false;
    }

    private void fill_fields() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                googlefit_data_loaded = true;
                year_step = 0;
                for (int x = 0; x < pasos.length; x++) {
                    year_step += pasos[x];
                }
                TextView textView = (TextView) findViewById(R.id.textView_year_value_step);
                textView.setText(String.valueOf(year_step));
                Log.i(TAG, "Total steps this year: " + year_step + " (Days: " + pasos.length + ")");
                Calendar cal = Calendar.getInstance();
                Date now = new Date();
                cal.setTime(now);
                int days_in_month = cal.get(Calendar.DAY_OF_MONTH);
                month_step = 0;
                int y = pasos.length - 1;
                for (int x = 1; x <= days_in_month; x++) {
                    month_step += pasos[y];
                    y--;
                }
                textView = (TextView) findViewById(R.id.textView_month_value_step);
                textView.setText(String.valueOf(month_step));
                Log.i(TAG, "Total steps this month: " + month_step + " (Days: " + days_in_month + ")");
                int days_in_week = cal.get(Calendar.DAY_OF_WEEK) - 1;
                week_step = 0;
                y = pasos.length - 1;
                for (int x = 1; x <= days_in_week; x++) {
                    week_step += pasos[y];
                    y--;
                }
                textView = (TextView) findViewById(R.id.textView_week_value_step);
                textView.setText(String.valueOf(week_step));
                Log.i(TAG, "Total steps this week: " + week_step + " (Days: " + days_in_week + ")");
                day_step = pasos[pasos.length - 1];
                textView = (TextView) findViewById(R.id.textView_day_value_step);
                textView.setText(String.valueOf(day_step));
                Log.i(TAG, "Total steps today: " + day_step);
                average_step = year_step / pasos.length;
                Log.i(TAG, "Average steps per day: " + average_step);
                textView = (TextView) findViewById(R.id.textView_average_value_step);
                textView.setText(String.valueOf(average_step));
                year_distance = 0;
                for (int x = 0; x < distancias.length; x++) {
                    year_distance += distancias[x];
                }
                textView = (TextView) findViewById(R.id.textView_year_value_distance);
                textView.setText(String.valueOf(year_distance));
                Log.i(TAG, "Total distance this year: " + year_step + " (Days: " + distancias.length + ")");
                month_distance = 0;
                y = distancias.length - 1;
                for (int x = 1; x <= days_in_month; x++) {
                    month_distance += distancias[y];
                    y--;
                }
                textView = (TextView) findViewById(R.id.textView_month_value_distance);
                textView.setText(String.valueOf(month_distance));
                Log.i(TAG, "Total distance this month: " + month_distance + " (Days: " + days_in_month + ")");
                week_distance = 0;
                y = distancias.length - 1;
                for (int x = 1; x <= days_in_week; x++) {
                    week_distance += distancias[y];
                    y--;
                }
                textView = (TextView) findViewById(R.id.textView_week_value_distance);
                textView.setText(String.valueOf(week_distance));
                Log.i(TAG, "Total distance this week: " + week_distance + " (Days: " + days_in_week + ")");
                day_distance = distancias[distancias.length - 1];
                textView = (TextView) findViewById(R.id.textView_day_value_distance);
                textView.setText(String.valueOf(day_distance));
                Log.i(TAG, "Total distance today: " + day_distance);
                average_distance = year_distance / distancias.length;
                Log.i(TAG, "Average distance per day: " + average_distance);
                textView = (TextView) findViewById(R.id.textView_average_value_distance);
                textView.setText(String.valueOf(average_distance));
            }
        });
    }
}
