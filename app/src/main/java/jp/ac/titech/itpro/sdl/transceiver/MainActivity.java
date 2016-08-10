package jp.ac.titech.itpro.sdl.transceiver;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "MainActivity";
    private TextView statusText;
    private ProgressBar connectionProgress;
    private EditText inputText;
    private Button sendButton, recognizeButton, speakButton;
    private ListView chatLogList;

    private static final int VOICE_RECOGNIZE_CODE = 3141;

    private ArrayList<ChatMessage> chatLog;
    private ArrayAdapter<ChatMessage> chatLogAdapter;

    private BluetoothAdapter btAdapter;
    private static final int REQUEST_ENABLE_BT = 1111;
    private final static int REQCODE_GET_DEVICE = 2222;
    private final static int REQCODE_DISCOVERABLE = 3333;

    private final static int MESG_STARTED = 1111;
    private final static int MESG_RECEIVED = 2222;
    private final static int MESG_FINISHED = 3333;
    private CommThread commThread;
    private int message_seq = 0;


    private final static String SPP_UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB";
    private final static UUID SPP_UUID = UUID.fromString(SPP_UUID_STRING);

    private final static int SERVER_TIMEOUT_SEC = 90;



    public enum State {
        Initializing,
        Disconnected,
        Connecting,
        Connected,
        Waiting
    }

    private State state = State.Initializing;

    private void setState(State state, String arg) {
        this.state = state;
        switch (state) {
            case Initializing:
            case Disconnected:
                statusText.setText(R.string.state_disconnected);
                inputText.setEnabled(false);
                sendButton.setEnabled(false);
                break;
            case Connecting:
                statusText.setText(getString(R.string.state_connecting) + arg);
                inputText.setEnabled(false);
                sendButton.setEnabled(false);
                break;
            case Connected:
                statusText.setText(getString(R.string.state_connected) + arg);
                inputText.setEnabled(true);
                sendButton.setEnabled(true);
                break;
            case Waiting:
                statusText.setText(R.string.state_waiting);
                inputText.setEnabled(false);
                sendButton.setEnabled(false);
                break;
        }
        invalidateOptionsMenu();

    }

    private String devName = "?";

    TextToSpeech tts = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = (TextView) findViewById(R.id.status_text);
        connectionProgress = (ProgressBar) findViewById(R.id.connection_progress);
        inputText = (EditText) findViewById(R.id.input_text);
        sendButton = (Button) findViewById(R.id.send_button);
        speakButton = (Button) findViewById(R.id.speak_button);
        recognizeButton = (Button) findViewById(R.id.recognize_button);
        chatLogList = (ListView) findViewById(R.id.chat_log_list);

        tts = new TextToSpeech(this, this);

        recognizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent voiceRecognizeIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    voiceRecognizeIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    voiceRecognizeIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, MainActivity.this.getString(R.string.voice_recognize_prompt));
                    startActivityForResult(voiceRecognizeIntent, VOICE_RECOGNIZE_CODE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "ActivityNotFoundException", Toast.LENGTH_LONG).show();
                    inputText.setText(e.toString());
                }
            }
        });
        speakButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speechText();
            }
        });

        chatLog = new ArrayList<ChatMessage>();
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            Toast.makeText(this, "This device does not support Bluetooth.", Toast.LENGTH_SHORT);
            finish();
            return;
        }

        if (!btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            setupBT();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case VOICE_RECOGNIZE_CODE:
                if (resultCode == RESULT_OK) {
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    Toast.makeText(this, result.get(0), Toast.LENGTH_LONG).show();
                    EditText editText = (EditText) MainActivity.this.findViewById(R.id.input_text);
                    editText.setText("");
                    editText.append(result.get(0));
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    setupBT();
                } else {
                    Log.d(TAG, getString(R.string.BT_not_enabled));
                    Toast.makeText(this, getString(R.string.BT_not_enabled), Toast.LENGTH_SHORT);
                    finish();
                    return;
                }
            case REQCODE_GET_DEVICE:
                if (resultCode == Activity.RESULT_OK)
                    connect1((BluetoothDevice) data.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
                else
                    setState(State.Disconnected, null);
                break;
            case REQCODE_DISCOVERABLE:
                startServer1();
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onInit(int status) {
        if (TextToSpeech.SUCCESS == status) {
            Locale locale = Locale.JAPANESE;
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                tts.setLanguage(locale);
            } else {
                Log.d(TAG, "Error Set Locale");
            }
        } else {
            Log.d(TAG, "Error Init");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.shutdown();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu");
        MenuItem itemConnect = menu.findItem(R.id.menu_connect);
        MenuItem itemDisconnect = menu.findItem(R.id.menu_disconnect);
        MenuItem itemServerStart = menu.findItem(R.id.menu_server_start);
        MenuItem itemServerStop = menu.findItem(R.id.menu_server_stop);
        switch (state) {
            case Initializing:
            case Connecting:
                itemConnect.setVisible(false);
                itemDisconnect.setVisible(false);
                itemServerStart.setVisible(false);
                itemServerStop.setVisible(false);
                return true;
            case Disconnected:
                itemConnect.setVisible(true);
                itemDisconnect.setVisible(false);
                itemServerStart.setVisible(true);
                itemServerStop.setVisible(false);
                return true;
            case Connected:
                itemConnect.setVisible(false);
                itemDisconnect.setVisible(true);
                itemServerStart.setVisible(false);
                itemServerStop.setVisible(false);
                return true;
            case Waiting:
                itemConnect.setVisible(false);
                itemDisconnect.setVisible(false);
                itemServerStart.setVisible(false);
                itemServerStop.setVisible(true);
                return true;
            default:
                return super.onPrepareOptionsMenu(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()) {
            case R.id.menu_connect:
                connect();
                return true;
            case R.id.menu_disconnect:
                disconnect();
                return true;
            case R.id.menu_server_start:
                startServer();
                return true;
            case R.id.menu_server_stop:
                stopServer();
                return true;
            case R.id.menu_clear:
                chatLogAdapter.clear();
                return true;
            case R.id.menu_about:
                new AlertDialog.Builder(this)
                        .setTitle("About BTChat")
                        .setMessage("Simple Bluetooth chat application")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void speechText() {
        String text = ((EditText) findViewById(R.id.input_text)).getText().toString();
        if (text.length() > 0) {
            if (tts.isSpeaking()) {
                tts.stop();
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private void setupBT() {
        Log.d(TAG, "setupBT");
        devName = btAdapter.getName();
        setState(State.Disconnected, null);
    }

    private void connect() {
        Log.d(TAG, "connect");
        Intent intent = new Intent(this, BTScanActivity.class);
        startActivityForResult(intent, REQCODE_GET_DEVICE);
    }

    private void connect1(BluetoothDevice device) {
        Log.d(TAG, "connect1");
        clientTask = new ClientTask();
        clientTask.execute(device);
        setState(State.Connecting, device.getName());
    }

    private void disconnect() {
        Log.d(TAG, "disconnect");
        if (commThread != null) {
            commThread.close();
            commThread = null;
        }
        setState(State.Disconnected, null);
    }

    private void startServer() {
        Log.d(TAG, "startServer");
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, SERVER_TIMEOUT_SEC);
        startActivityForResult(intent, REQCODE_DISCOVERABLE);
    }

    private void startServer1() {
        Log.d(TAG, "startServer1");
        serverTask = new ServerTask();
        serverTask.execute(SERVER_TIMEOUT_SEC);
        setState(State.Waiting, null);
    }

    private void stopServer() {
        Log.d(TAG, "stopServer");
        if (serverTask != null)
            serverTask.stop();
    }

    public void onClickSendButton(View v) {
        Log.d(TAG, "onClickSendButton");
        if (commThread != null) {
            String content = inputText.getText().toString().trim();
            if (content.length() == 0) {
                Toast.makeText(this, R.string.message_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            message_seq++;
            long time = System.currentTimeMillis();
            ChatMessage message = new ChatMessage(message_seq, time, content, devName);
            commThread.send(message);
            chatLogAdapter.add(message);
            chatLogAdapter.notifyDataSetChanged();
            chatLogList.smoothScrollToPosition(chatLog.size());
            inputText.getEditableText().clear();
        }
    }

    private ClientTask clientTask;
    private ServerTask serverTask;

    private class ClientTask extends AsyncTask<BluetoothDevice, Void, BluetoothSocket> {
        private final static String TAG = "ClientTask";

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "onPreExecute");
            connectionProgress.setIndeterminate(true);
        }

        @Override
        protected BluetoothSocket doInBackground(BluetoothDevice... params) {
            Log.d(TAG, "doInBackground");
            BluetoothSocket socket = null;
            try {
                socket = params[0].createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
            } catch (IOException e) {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    socket = null;
                }
            }
            return socket;
        }

        @Override
        protected void onPostExecute(BluetoothSocket socket) {
            Log.d(TAG, "onPostExecute");
            connectionProgress.setIndeterminate(false);
            if (socket == null) {
                Toast.makeText(MainActivity.this, R.string.connection_failed, Toast.LENGTH_SHORT).show();
                setState(State.Disconnected, null);
            } else {
                try {
                    commThread = new CommThread(socket);
                    commThread.start();
                } catch (IOException e) {
                    try {
                        socket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    setState(State.Disconnected,null);
                }
            }
            clientTask = null;
        }
    }

    private class ServerTask extends AsyncTask<Integer, Void, BluetoothSocket> {
        private final static String TAG = "ServerTask";
        private BluetoothServerSocket serverSocket;

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "onPreExecute");
            connectionProgress.setIndeterminate(true);
        }

        @Override
        protected BluetoothSocket doInBackground(Integer... params) {
            Log.d(TAG, "doInBackground");
            BluetoothSocket socket = null;
            try {
                serverSocket = btAdapter.listenUsingRfcommWithServiceRecord(devName, SPP_UUID);
                socket = serverSocket.accept(params[0] * 1000);
            } catch (IOException e) {
                socket = null;
            } finally {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return socket;
        }

        @Override
        protected void onPostExecute(BluetoothSocket socket) {
            Log.d(TAG, "onPostExecute: socket=" + socket);
            connectionProgress.setIndeterminate(false);
            if (socket == null) {
                Toast.makeText(MainActivity.this, R.string.connection_failed, Toast.LENGTH_SHORT).show();
                setState(State.Disconnected, null);
            } else {
                try {
                    commThread = new CommThread(socket);
                    commThread.start();
                } catch (IOException e) {
                    try {
                        socket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    setState(State.Disconnected, null);
                }
            }
            serverTask = null;
        }

        @Override
        protected void onCancelled() {
            Log.d(TAG, "onCancelled: serverSocket=" + serverSocket);
            connectionProgress.setIndeterminate(false);
            setState(State.Disconnected, null);
            serverTask = null;
        }

        public void stop() {
            Log.d(TAG, "stop");
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            cancel(false);
        }
    }
    private Handler commHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            Log.d(TAG, "handleMessage");
            switch (msg.what) {
                case MESG_STARTED:
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    setState(State.Connected, device.getName());
                    break;
                case MESG_FINISHED:
                    Toast.makeText(MainActivity.this, R.string.connection_closed, Toast.LENGTH_SHORT).show();
                    setState(State.Disconnected, null);
                    break;
                case MESG_RECEIVED:
                    chatLogAdapter.add((ChatMessage) msg.obj);
                    chatLogAdapter.notifyDataSetChanged();
                    chatLogList.smoothScrollToPosition(chatLogAdapter.getCount());
                    break;
                default:
            }
            return false;
        }
    });

    private class CommThread extends Thread {
        private final static String TAG = "CommThread";
        private final BluetoothSocket socket;
        private final ChatMessage.Reader reader;
        private final ChatMessage.Writer writer;
        public CommThread(BluetoothSocket socket) throws IOException {
            if (!socket.isConnected())
                throw new IOException("Socket is not connected");
            this.socket = socket;
            reader = new ChatMessage.Reader(new JsonReader(new InputStreamReader(socket.getInputStream(), "UTF-8")));
            writer = new ChatMessage.Writer(new JsonWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8")));

        }
        @Override
        public void run() {
            Log.d(TAG, "run");
            commHandler.sendMessage(commHandler.obtainMessage(MESG_STARTED, socket.getRemoteDevice()));
            try {
                writer.beginArray();
                reader.beginArray();
                while (reader.hasNext())
                    commHandler.sendMessage(commHandler.obtainMessage(MESG_RECEIVED, reader.read()));
                } catch  (Exception e) {
                Log.d(TAG, "reader exception");
            } finally {
                if (socket.isConnected()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            commHandler.sendMessage(commHandler.obtainMessage(MESG_FINISHED));
        }

        public void send(ChatMessage message) {
            try {
                writer.write(message);
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void close() {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}


