package com.zsoft.ivan.ikasfit;
//antes de poder usar api de google +, googlefit y parse
//tener en build.gradle definidas las dependencias
//y androidmanifest.xml los permisos necesarios

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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
import android.widget.ImageView;
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
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;
import com.parse.FunctionCallback;
import com.parse.GetCallback;
import com.parse.Parse;
import com.parse.ParseCloud;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.text.DateFormat.getDateTimeInstance;


public class MainActivity extends AppCompatActivity {
    //declaracion de variables
    public static final String TAG = "IkasFit";
    private boolean connected_googlefit = false;
    private boolean googlefit_data_loaded = false;
    private boolean data_deleted = false;
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    private GoogleApiClient mClient = null;
    private int[] pasos;
    private float[] distancias;
    int year_step, month_step, week_step, day_step, average_step;
    float year_distance, month_distance, week_distance, day_distance, average_distance;
    String gmail = null;

    //aqui se realizan todas las operaciones cuando se crea el Activity
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //aqui se podrian hacer distintas cosas si el activity se vuelve a crear por volver de segundo palno
        //o rotar la pantalla como por ejemplo guardar valores previos
        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }
        //pongo el icono de la aplicacion en la barra de menu
        ActionBar actionBar = getSupportActionBar();
        actionBar.setLogo(R.mipmap.ic_launcher);
        actionBar.setDisplayUseLogoEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        //inicio el logger, que me servirá tanto como para mandar mensajes al registro
        //como a la aplicación ya que la clase LogView extiende a TextViex y puedo
        //ponerla en el Layout
        initializeLogging();
        //el logger esta dentro de un scrollview
        //asi que le eñado un listener que detecta cuando ha cambiado el texto que tiene dentro
        //estas obligado a sobreescribiur estos tres metodos
        //pero solo añado codigo al que me interesa
        LogView logView = (LogView) findViewById(R.id.sample_logview);
        logView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            //una vez que el texto ha cambiado pondremos en la cola del hilo principal
            //la orden de bajar el scroll hasta abajo
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
        // [Optional] Power your app with Local Datastore. For more info, go to
        // https://parse.com/docs/android/guide#local-datastore
        //esto en realidad no lo he terminado usando y no he guardado datos localmente
        Parse.enableLocalDatastore(this);
        Parse.initialize(this);
    }

    //metodo que inicializa el logger y nos deja un primer mensaje informativo
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

    //sobreescribo para indicar que se "infle" nuestro menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    //en este metodo defino que ocurrira al seleccionar las distintas opciones del menui
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //guardo la id de que elemento ha sido seleccionado
        int id = item.getItemId();
        //y comparo con los distintos elementos del menu
        //si ha dado a la opcion de borrar los datos de googlefit
        if (id == R.id.action_delete_data) {
            //si estamos conectados a googlefit
            if (connected_googlefit) {
                //creo un dialogo que nos avisa del peligro de borrar los datos de googlefit
                //esta opcion es valida para una aplicacion de pruebas no para un lanzamiento
                AlertDialog.Builder adb = new AlertDialog.Builder(this, android.R.style.Theme_Dialog);
                adb.setTitle("¡PELIGRO!");
                adb.setMessage("¿Estás seguro de borrar los datos de pasos y distancia de Google Fit para el año en curso?");
                adb.setIcon(android.R.drawable.ic_dialog_alert);
                adb.setPositiveButton("BORRAR", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //si le da a que si
                        //llamo a un metodo que borra los datos
                        deleteData();
                        //cambio el valor de esta variable para que en otro punto del programa
                        //permita insertar nuevos datos
                        data_deleted = true;
                        //vacio los campos de la aplicacion,
                        //ya que ya no representan la realidad de lo que hay en googlefit
                        empty_fields();
                    }
                });
                adb.setNegativeButton("CANCELAR", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                adb.show();
            } else { // si no esta conectado a googlefit aun no da error
                Toast.makeText(this, "¡No estás conectado a Google Fit!",
                        Toast.LENGTH_LONG).show();
            }
            return true;
            //si la opcion es insertar datos
        } else if (id == R.id.action_insert_random_data) {
            //si estamos conectados a googlefit
            if (connected_googlefit) {
                //y ademas se han borrado los datos
                if (data_deleted) {
                    //llamo a esta clase tipo AsyncTask y la ejecuto
                    //se encargara de meter nuevos datos en googlefit
                    //esta opcion es adecuada solo para una aplicacion enm pruebas
                    //y no para un lanzamiento
                    new DataInserter().execute();
                    //vuelvo esta variable a su estado original
                    data_deleted = false;
                    //y vacio los campos pq ya no reflejan lo que hay google fit
                    empty_fields();
                    //si no se cumplen lascondiciones para insertar datos informo al usuario
                    // de porque no se puede
                } else {
                    Toast.makeText(this, "¡Borra los datos antes de poder insertar nuevos!",
                            Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "¡No estás conectado a Google Fit!",
                        Toast.LENGTH_LONG).show();
            }
            return true;
            //si le damos al boton de googlefit
        } else if (id == R.id.action_googlefit) {
            //si no estamos conectados aun conectamos creando un nuevo cliente
            if (!connected_googlefit) {
                buildFitnessClient();
                //y si ya estabamos conectados simplemente refrescamos los datos
            } else {
                new DataRequester().execute();
                return true;
            }
            //si le damos al boton de parse
        } else if (id == R.id.action_parse) {
            //controlo primero que se den las circustancias adecuadas para acceder a parse
            //necesito que se haya conectado primero con googlefit
            //y que sus datos esten cargados pq se van a subir al servidor parse
            if (!connected_googlefit) {
                Toast.makeText(this, "¡Conecta primero con Google Fit!",
                        Toast.LENGTH_LONG).show();
            } else if (!googlefit_data_loaded) {
                Toast.makeText(this, "¡Carga primero los datos de Google Fit!",
                        Toast.LENGTH_LONG).show();
            } else {
                //para este primer paso he decidido no usar una funcion en la nube para probar
                //pero se podria perfectamente usando el mail de la cuenta como parametro
                //esta consulta busca en todos los objetos del tipo IkasFit en parse el primero
                //que en mi caso seria el unico tambien
                //que en el campo "account" tenga el mismo correo que la cuenta de usuario del movil
                // esto lo hago por el siguiente motivo
                //si el objeto es nulo significa que es la primera vez que usa la aplicacion
                //y se creara un nuevo registro
                //si devuelvo un objeto usare ese objeto para sobreescrinbir la informacion en el
                ParseQuery<ParseObject> query = ParseQuery.getQuery("IkasFit");
                query.whereEqualTo("account", gmail);
                query.getFirstInBackground(new GetCallback<ParseObject>() {
                    public void done(ParseObject object, ParseException e) {
                        if (object == null) {
                            Log.i(TAG, "Fist time uploading to parse, creating object.");
                            object = new ParseObject("IkasFit");
                        } else {
                            Log.i(TAG, "User already exists in parse, updating object.");
                        }
                        object.put("account", gmail);
                        object.put("year_step", year_step);
                        object.put("month_step", month_step);
                        object.put("week_step", week_step);
                        object.put("average_step", average_step);
                        object.put("year_distance", year_distance);
                        object.put("month_distance", month_distance);
                        object.put("week_distance", week_distance);
                        object.put("average_distance", average_distance);
                        object.saveInBackground();
                        Log.i(TAG, "Steps and distance data uploaded to Parse");
                        //ya he hecho enviado mis datros a parse
                        //ahora toca recibir ciertas estadisticas para rellenar los campos de la aplicacion
                        //en este caso en vez de hacer una consulta desde la aplicacion voy a usar
                        //funciones que estan en la nube de parse, que es mejor manera
                        //para usar las funciones desde cualquier plataforma sin programar las consultas de manera
                        //distinta segun el lenguaje
                        //la primera funcion nos devuelve el numero total de usuarios
                        HashMap<String, Object> params = new HashMap<String, Object>();
                        ParseCloud.callFunctionInBackground("total_users", params, new FunctionCallback<Integer>() {
                            public void done(Integer respuesta, ParseException e) {
                                if (e == null) {
                                    Log.i(TAG, "Cloud code - Total users: " + respuesta);
                                    TextView textView = (TextView) findViewById(R.id.textView_total_value);
                                    textView.setText(String.valueOf(respuesta));
                                } else {
                                    Log.i(TAG, "Cloud code error: " + e.toString());
                                }
                            }
                        });
                        //la segunda funcion nos devuelve el usuario que mas distancia ha recorrido
                        params = new HashMap<String, Object>();
                        ParseCloud.callFunctionInBackground("number1", params, new FunctionCallback<String>() {
                            public void done(String respuesta, ParseException e) {
                                if (e == null) {
                                    Log.i(TAG, "Cloud code - Number 1 user; " + respuesta);
                                    TextView textView = (TextView) findViewById(R.id.textView_number1_value);
                                    textView.setText(respuesta);
                                } else {
                                    Log.i(TAG, "Cloud code error: " + e.toString());
                                }
                            }
                        });
                        //la tercera y ultima funcion nos devuelve nuestra posicion en el ranking de distancias recorridas
                        params = new HashMap<String, Object>();
                        params.put("account", gmail);
                        ParseCloud.callFunctionInBackground("ranking", params, new FunctionCallback<Integer>() {
                            public void done(Integer respuesta, ParseException e) {
                                if (e == null) {
                                    Log.i(TAG, "Cloud code - Your ranking; " + respuesta);
                                    TextView textView = (TextView) findViewById(R.id.textView_ranking_value);
                                    textView.setText(String.valueOf(respuesta) + "º");
                                } else {
                                    Log.i(TAG, "Cloud code error: " + e.toString());
                                }
                            }
                        });
                    }
                });

            }
            //si han dado a la opcion acerca de
        } else if (id == R.id.action_about) {
            //muestro un dialogo informativo que nos dice un poco que es esto y quien lo hace y para que
            AlertDialog.Builder adb = new AlertDialog.Builder(this, android.R.style.Theme_Dialog);
            adb.setTitle("Acerca de");
            adb.setMessage("IKasFit v1.0\nProyecto fin de curso\nDesarrollo de Aplicaciones Multiplataforma\nEGIBIDE\nmotxuelodon@gmail.com\n\nAndroidStudio NetBeans Parse.com CanvasJS GitHub");
            adb.setIcon(android.R.drawable.ic_dialog_info);
            adb.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            adb.show();
            //si le da al boton del navegador
        } else if (id == R.id.action_www) {
            Log.i(TAG, "Opening 'http://ikasfit-zsoft.parseapp.com' on default web browser");
            //abro el navegador en otro activity y muestro la pagina web que tengo subida en parse
            //alli se mostraran las graficas comparativas de los distintos usuarios
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://ikasfit-zsoft.parseapp.com"));
            startActivity(browserIntent);
        }
        return super.onOptionsItemSelected(item);
    }

    //este metodo se llamadesde el boton de GoogleFit en el menu
    //y crea el cliente y nos conecta
    private void buildFitnessClient() {
        mClient = new GoogleApiClient.Builder(this)
                //api necesaria para los datos actividad
                .addApi(Fitness.HISTORY_API)
                        //api necesaria para los datos de la cuenta
                .addApi(Plus.API)
                        //para los datos de la cuenta
                .addScope(new Scope(Scopes.PLUS_LOGIN))
                        //para los datos de los pasos
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                        //para los datos de la distancia
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ_WRITE))
                        //lo que pasa al comunicarte
                .addConnectionCallbacks(
                        new ConnectionCallbacks() {
                            @Override
                            //lo que pasa si conecto
                            public void onConnected(Bundle bundle) {
                                //guardo en esta variable los datos de la cuenta
                                //para usarlos mas tarde
                                Person person = Plus.PeopleApi.getCurrentPerson(mClient);
                                ActionBar actionBar = getSupportActionBar();
                                //pongo el nombre del usuario en la barra de accion
                                //no uso este nombre como usuario, si no el correo, que si es unico
                                actionBar.setSubtitle(person.getDisplayName());
                                //quiero poner la imagen que el usuario haya puesto en su cuenta de google
                                //en la aplicacion hara que quede claro que esta conectado
                                //para ello necesito la url donde esta esa imagen
                                String photo_url = person.getImage().getUrl();
                                //modifico el final de la cadena de texto de la url para indicar el tamaño que quiero
                                //por defecto era 50
                                photo_url = photo_url.substring(0, photo_url.length() - 2) + "100";
                                //creo un nuevo objeto de una clase AsyncTask pasandole que ImageView es la que tendra la imagen
                                //y como parametro la url de donde cogerla
                                new LoadProfileImage((ImageView) findViewById(R.id.image_Acount)).execute(photo_url);
                                //por ultimo cojo el correo que usare de nombre de usuario en parse
                                gmail = Plus.AccountApi.getAccountName(mClient);
                                TextView textView = (TextView) findViewById(R.id.title_text_view);
                                textView.setText(gmail);
                                Log.i(TAG, "Connected to Google Fit!!!");
                                connected_googlefit = true;
                                //ahora que ya tengo lo que quiero de la cuenta
                                //llamare a esta otra clase que recoge los datos de googlefir
                                //esta clase es la misma que se llama si le hubieras dado al boton de googlefit y ya hubieras estado conectado
                                new DataRequester().execute();
                            }

                            //lo que pasa si se suspende la conexion
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
                        //lo que pasa cuando no llegas a comunicar
                .enableAutoManage(this, 0, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        empty_fields();
                        connected_googlefit = false;
                        Log.i(TAG, "Google Play services connection failed. Cause: " +
                                result.toString());
                        //otra forma de dar mensajes al usuario que no son las tostadas
                        Snackbar.make(
                                MainActivity.this.findViewById(R.id.main_activity_view),
                                "Exception while connecting to Google Play services: " +
                                        result.getErrorMessage(),
                                Snackbar.LENGTH_INDEFINITE).show();
                    }
                })
                .build();
    }

    //primera de las clases de este tipo de las varias que hay en la aplicacion.
    //como no es un codigo largo las dejo aqui como private en vez de en otro archivo .java
    //este tipo de clases nos permite añadir codigo que se ejecutará en segundo plano, en otro hilo
    //sin tener que manejar Threads, pero sigues teniendo que tener en cuenta si es necesario
    //controlar algun tipo de concurrencia
    private class LoadProfileImage extends AsyncTask<String, Void, Bitmap> {
        //tomamos la ImageView que se nos pasa y en el constructor la guardamois en una variable local
        ImageView profile_photo;

        public LoadProfileImage(ImageView profile_photo) {
            this.profile_photo = profile_photo;
        }

        //ahora ya indicamos que se va a hacer en segundo plano
        protected Bitmap doInBackground(String... urls) {
            //atraves de un flujo guardamos en un bitmap la imagen de la url
            String urldisplay = urls[0];
            Bitmap photo = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                photo = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return photo;
        }

        //con el trabajo hecho ya solo queda poner la imagen en el layou
        protected void onPostExecute(Bitmap result) {
            profile_photo.setImageBitmap(result);
        }
    }

    //esta clase insertara datos ficticios en googlefit
    private class DataInserter extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            //los datos de los pasos que voy a insertar los genero en un metodo a parte
            //que devuelve un dataset
            DataSet dataSet = insertFitnessData_steps();
            Log.i(TAG, "Inserting the dataset of steps in the History API");
            //con los datos generados intento insertarlos
            com.google.android.gms.common.api.Status insertStatus =
                    Fitness.HistoryApi.insertData(mClient, dataSet)
                            .await(1, TimeUnit.MINUTES);
            //si ha ido bien o mal lo  cominico al usuario
            if (!insertStatus.isSuccess()) {
                Log.i(TAG, "There was a problem inserting the dataset of steps.");
                return null;
            } else {
                Log.i(TAG, "Steps data insert was successful!");
            }
            //lo mismo que hemos hecho para insertar los datos de los pasos lo hago para las distancias
            dataSet = insertFitnessData_distance();
            Log.i(TAG, "Inserting the dataset of distance in the History API");
            insertStatus = Fitness.HistoryApi.insertData(mClient, dataSet)
                    .await(1, TimeUnit.MINUTES);
            if (!insertStatus.isSuccess()) {
                Log.i(TAG, "There was a problem inserting the dataset of distance.");
                return null;
            } else {
                Log.i(TAG, "Distance data insert was successful!");
            }
            return null;
        }
    }

    //este metodo es el que genera los datos aleatorios de los pasos
    //que se usaran para insertarlos en googlefit
    private DataSet insertFitnessData_steps() {
        Log.i(TAG, "Creating a new data insert request for steps");
        //hay que definior primero que tipo de datos con un origen de datos
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(this)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setName(TAG + " - step count")
                .setType(DataSource.TYPE_RAW)
                .build();
        DataSet dataSet = DataSet.create(dataSource);
        //usare un objeto de tipo calendario para controlar a que franjas de tiempo
        //pertenecen los datos
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        int days = cal.get(Calendar.DAY_OF_YEAR);
        Log.i(TAG, "Days in this year:" + String.valueOf(days));
        //como voy a añadir datos por cada uno de los dias que hayan transcurrido en el presente año
        // pongo el calendario a a dia 1 de enero a las 00:00
        cal.set(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        //recorro tantos dias como hay desde el 1 de enero hasta hoy
        for (int i = 1; i <= days; i++) {
            //la fecha y hora de inicio es la guardada en el calendario
            long startTime = cal.getTimeInMillis();
            //la fecha fiinal la sacaremos del calendario ajustando este primero
            //si ya hemos llegado al dia de hoy será ahora
            if (i == days) {
                cal.setTime(now);
                //si no, sera sumarle 1 dia y quitarle un segundo
            } else {
                cal.add(Calendar.DATE, 1);
                cal.add(Calendar.SECOND, -1);
            }
            //asi queda la misma fecha pero la hora 23:59:59
            long endTime = cal.getTimeInMillis();
            //añado el segundo que falta para el dia ssiguiente para tener el calendario listo para el dia siguiente
            //en la posible nueva vuelta del bucle
            cal.add(Calendar.SECOND, +1);
            //genero con random la cantidad de pasos
            Random rand = new Random();
            int randomSteps = rand.nextInt((10000 - 100) + 1) + 100;
            DateFormat dateFormat = getDateTimeInstance();
            Log.i(TAG, "Insert from " + dateFormat.format(startTime) + " to " + dateFormat.format(endTime) + " - " + randomSteps + " steps");
            //creo un datapoint, con la hora de inicio, la hora final y el numero de pasos
            DataPoint dataPoint = dataSet.createDataPoint()
                    .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS);
            dataPoint.getValue(Field.FIELD_STEPS).setInt(randomSteps);
            //ese datapoint creado lo añadimos al dataset que devolvera finalmente el metodo
            dataSet.add(dataPoint);
        }
        return dataSet;
    }

    //este metodo es el que genera los datos aleatorios de la distancia
    //que se usaran para insertarlos en googlefit
    //es practicamente identico al de los pasos
    //pero he preferido dejarlos separados de tal manera que si hay errores al insertarlos puedan insertarse
    //los pasos si y la distancia no o al reves
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

    //esta clase hara que se recuperen los datos en segundo plano llamando a un metodo para ello
    private class DataRequester extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            DataReadRequest readRequest = queryFitnessData();
            DataReadResult dataReadResult =
                    Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);
            //una vez recogidos los datos uso el siguiente metodo para mostrarlos en pantalla
            printData(dataReadResult);
            return null;
        }
    }

    //metodo que se llama desde la clase datarequester
    //para recuperar los datos
    private DataReadRequest queryFitnessData() {
        //usare de nuevo un calendario para marcar el inicio y el fin
        //en  este caso de la consulta
        //que sera desde que comenzo el año hasta ahora
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
        //preaparado el inicio y el fin ya solo me queda hacer la peticion
        //indicando que tipos de datos quiero recoger de los multiples que hay en googlefit
        //e indicando tambien que no quiero todos juntos
        //si no en partes dividivas en franjas de 1 dia, los buckets
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();
        return readRequest;
    }

    //este metodo nos mostrara los datos recuperados de googlfit
    private void printData(DataReadResult dataReadResult) {
        //si se han devuelto datos, por lo menos un bucket, ose a1 dia
        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Number of returned buckets of DataSets is: "
                    + dataReadResult.getBuckets().size());
            //inializo y doy tamaño a los arrays donde se guardaran los datosn de cada dia
            pasos = new int[dataReadResult.getBuckets().size()];
            distancias = new float[dataReadResult.getBuckets().size()];
            int x = 0;
            //ahora ya puedo recorrer cada bucket, cada dia
            for (Bucket bucket : dataReadResult.getBuckets()) {
                //como un bucket puede tener varios conjuntos de datos hay que recorrerlos
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    //este metodo fianlmente hara el vaciado de datos en los arryas listos para ello
                    dumpDataSet(dataSet, x);
                }
                x++;
            }
            //ahora que tengo los datos de googlefit en mi poder
            //puedo llamar al siguiente metodo para rellenar los campos en pantalla de la aplicacion
            fill_fields();
        }
    }

    //metodo que realiza el vaciado de datos en unos array preparados para ello
    private void dumpDataSet(DataSet dataSet, int index) {
        DateFormat dateFormat = getDateTimeInstance();
        int paso = 0;
        float distancia = 0;
        //un dataset se compone de de varios datapoints
        //los recorrere todos y sumare sus valores , tanto para pasos como para distancia,
        //para sacar el total de ese bucket, ose de ese dia
        for (DataPoint dp : dataSet.getDataPoints()) {
            Log.i(TAG, "Data point:");
            Log.i(TAG, "\tType: " + dp.getDataType().getName());
            Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            //si el tipo de dato es del tipo que a mi me interesa, eseete caso los pasos
            //lo cojo y lo sumo
            if (dp.getDataType().getName().equalsIgnoreCase(DataType.TYPE_STEP_COUNT_DELTA.getName())) {
                for (Field field : dp.getDataType().getFields()) {
                    Log.i(TAG, "\tField: " + field.getName() +
                            " Value: " + dp.getValue(field));
                    paso += dp.getValue(field).asInt();
                }
                //una vez sumados todos los posibles datapoints de pasos de ese dia
                //los añado a mi array de datos, en la posicion del indice pasado por quien llamo al metodo
                //ese indice representa el numero de bucket
                //y por lo tanto el numero de dia desde que comenzo el año
                pasos[index] = paso;
                //lo mismo para los pasos ahora para las distancias
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

    //en un principio el vaciado de datos lo hice separado en 2 metodos
    //uno para pasos y otro para distancia
    //luego los junte pero no borro estos por si cambnio la aplicacion
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

    //este metodo borra los datos de googlefit
    private void deleteData() {
        Log.i(TAG, "Deleting last year's step and distance count data");
        //una vez mas uso un calendario e indico el inicio y el fin del borrado
        //desde que comienza el año hasta ahora
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.set(Calendar.DAY_OF_YEAR, 1);
        long startTime = cal.getTimeInMillis();
        //no quiero borrar nada mas quie los datos del tipo que uso en la aplicacion
        //pasos y distancia
        DataDeleteRequest request = new DataDeleteRequest.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .addDataType(DataType.TYPE_DISTANCE_DELTA)
                .build();
        //el callback nos dirá si el el resultado del borrado es satisfactorio o no,
        //y asi informar al usuario
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

    //este metodo vacia los campos de la pantalla
    //se le llama cuando se borran o insertan datos
    //pq los datos que contienen ya no serian una representacion real de lo que hay en googlfit
    private void empty_fields() {
        //tomo el layout de mi aplicacion
        RelativeLayout myRelativeLayout = (RelativeLayout) findViewById(R.id.main_activity_view);
        //recorro los elementos que contiene
        for (int i = 0; i < myRelativeLayout.getChildCount(); i++)
            //si el elemento es del tipo TextView es un candidao para vaciar su contenido
            if (myRelativeLayout.getChildAt(i) instanceof TextView) {
                int id = myRelativeLayout.getChildAt(i).getId();
                //pero no todos los textvies son para borrar
                //otros son solo etiquetas
                //asi que los cribo
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
        //esta variable impedira que sibir datos a parse hasta que no se carguen de nuevo los de googlefit
        googlefit_data_loaded = false;
    }

    //este metodo llenara los campos de la pantalla con los datos que habia guardado
    //en dos arrays cuando accedi a googlefit
    private void fill_fields() {
        //el problema es que este metodo se llama desde distintos hilos
        //que no pueden tener acceso a modificar el layout
        //por eso usamos la siguiente herramienta
        //que pone el proceso en cola del hilo principal
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                googlefit_data_loaded = true;
                //un poco de formato para que los nuemero sean mas entendibles en pantalla
                DecimalFormat decimalFormat = new DecimalFormat("##,###,###");
                //empiezo por el campor de pasos dados este año
                year_step = 0;
                //no deja de ser la suma de todos los dias guardados en el array
                for (int x = 0; x < pasos.length; x++) {
                    year_step += pasos[x];
                }
                TextView textView = (TextView) findViewById(R.id.textView_year_value_step);
                textView.setText(decimalFormat.format(year_step));
                Log.i(TAG, "Total steps this year: " + year_step + " (Days: " + pasos.length + ")");
                //para calcular el total del mes
                //hay que sacar cuantos dis lleva este mes transcurrido
                Calendar cal = Calendar.getInstance();
                Date now = new Date();
                cal.setTime(now);
                int days_in_month = cal.get(Calendar.DAY_OF_MONTH);
                month_step = 0;
                int y = pasos.length - 1;
                //sabiendo los dias del mes podemos recorrer el array de pasos para sacar ese total
                for (int x = 1; x <= days_in_month; x++) {
                    month_step += pasos[y];
                    y--;
                }
                textView = (TextView) findViewById(R.id.textView_month_value_step);
                textView.setText(decimalFormat.format(month_step));
                Log.i(TAG, "Total steps this month: " + month_step + " (Days: " + days_in_month + ")");
                //igual que para el mes pues para la semana
                int days_in_week = cal.get(Calendar.DAY_OF_WEEK) - 1;
                if (days_in_week == 0) {
                    days_in_week = 7;
                }
                week_step = 0;
                y = pasos.length - 1;
                for (int x = 1; x <= days_in_week; x++) {
                    week_step += pasos[y];
                    y--;
                }
                textView = (TextView) findViewById(R.id.textView_week_value_step);
                textView.setText(decimalFormat.format(week_step));
                Log.i(TAG, "Total steps this week: " + week_step + " (Days: " + days_in_week + ")");
                //los pasos del dia de hoy son simplemente el ultimo dato del array
                day_step = pasos[pasos.length - 1];
                textView = (TextView) findViewById(R.id.textView_day_value_step);
                textView.setText(decimalFormat.format(day_step));
                Log.i(TAG, "Total steps today: " + day_step);
                //y la media diaria es el total del año dividido por el total de dias,
                //o lo que es lo mismo
                //dividido por el tamaño del array
                average_step = year_step / pasos.length;
                Log.i(TAG, "Average steps per day: " + average_step);
                textView = (TextView) findViewById(R.id.textView_average_value_step);
                textView.setText(decimalFormat.format(average_step));
                //ahora lo que hemos hecho para los pasos lo hacemos para las distancias
                //eso si, las distancias son decimales medidas en metros,
                //asi que hay que darle un formato para que no muestre mas de dos decimales
                //o podrian quedar raros los datos en pantalla
                decimalFormat = new DecimalFormat("##,###,###.00");
                year_distance = 0;
                for (int x = 0; x < distancias.length; x++) {
                    year_distance += distancias[x];
                }
                textView = (TextView) findViewById(R.id.textView_year_value_distance);
                textView.setText(decimalFormat.format(year_distance));
                Log.i(TAG, "Total distance this year: " + year_step + " (Days: " + distancias.length + ")");
                month_distance = 0;
                y = distancias.length - 1;
                for (int x = 1; x <= days_in_month; x++) {
                    month_distance += distancias[y];
                    y--;
                }
                textView = (TextView) findViewById(R.id.textView_month_value_distance);
                textView.setText(decimalFormat.format(month_distance));
                Log.i(TAG, "Total distance this month: " + month_distance + " (Days: " + days_in_month + ")");
                week_distance = 0;
                y = distancias.length - 1;
                for (int x = 1; x <= days_in_week; x++) {
                    week_distance += distancias[y];
                    y--;
                }
                textView = (TextView) findViewById(R.id.textView_week_value_distance);
                textView.setText(decimalFormat.format(week_distance));
                Log.i(TAG, "Total distance this week: " + week_distance + " (Days: " + days_in_week + ")");
                day_distance = distancias[distancias.length - 1];
                textView = (TextView) findViewById(R.id.textView_day_value_distance);
                textView.setText(decimalFormat.format(day_distance));
                Log.i(TAG, "Total distance today: " + day_distance);
                average_distance = year_distance / distancias.length;
                Log.i(TAG, "Average distance per day: " + average_distance);
                textView = (TextView) findViewById(R.id.textView_average_value_distance);
                textView.setText(decimalFormat.format(average_distance));
                //todos los datos que este metodo muestra en pantalla tambien se han ido guardando en variables
                //estas variables son las que se usaran para subir la informacion que contienen a parse
                //y seran los datos que se utilicen para realizar las graficas
            }
        });
    }
}
