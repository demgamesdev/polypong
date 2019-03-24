package com.demgames.polypong;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;
import android.view.KeyEvent;
import android.widget.Toast;
import android.os.Vibrator;

import java.io.File;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class HostActivity extends AppCompatActivity{

    private static final String TAG = "HostActivity";
    private UpdateTask serverListUpdateTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Todo proper network handling in asynctask or thread
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
            //your codes here

        }

        //Vollbildmodus
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_host);

        /***Decklarationen***/

        final Globals globalVariables = (Globals) getApplicationContext();
        final Button startGameButton= (Button) findViewById(R.id.startGameButton);
        final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        globalVariables.getSettingsVariables().setupConnectionState =0;
        globalVariables.getSettingsVariables().resetArrayLists();
        globalVariables.getSettingsVariables().startServerThread();
        globalVariables.setListeners(getApplicationContext());
        globalVariables.getSettingsVariables().serverThread.getServer().addListener(globalVariables.getServerListener());
        globalVariables.getSettingsVariables().myPlayerNumber =0;
        globalVariables.getGameVariables().myPlayerNumber =globalVariables.getSettingsVariables().myPlayerNumber;


        //IP Suche
        serverListUpdateTask = new UpdateTask();
        serverListUpdateTask.execute();


        startGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                globalVariables.getSettingsVariables().ipAdresses= new ArrayList<String>(Arrays.asList(new String[] {}));
                globalVariables.getSettingsVariables().playerNames= new ArrayList<String>(Arrays.asList(new String[] {}));
                globalVariables.getSettingsVariables().ipAdresses.add(globalVariables.getSettingsVariables().myIpAdress);
                globalVariables.getSettingsVariables().playerNames.add(globalVariables.getSettingsVariables().myPlayerName);

                for(int i=0; i<globalVariables.getSettingsVariables().discoveryIpAdresses.size();i++) {
                    if(globalVariables.getSettingsVariables().discoveryIsChecked.get(i)){
                        globalVariables.getSettingsVariables().ipAdresses.add(globalVariables.getSettingsVariables().discoveryIpAdresses.get(i));
                        globalVariables.getSettingsVariables().playerNames.add(globalVariables.getSettingsVariables().discoveryPlayerNames.get(i));
                    }
                }

                if(globalVariables.getSettingsVariables().ipAdresses.size()>1) {

                    globalVariables.getSettingsVariables().numberOfPlayers = globalVariables.getSettingsVariables().ipAdresses.size();
                    globalVariables.getGameVariables().numberOfPlayers = globalVariables.getSettingsVariables().numberOfPlayers;

                    //globalVariables.getGameVariables().numberOfBalls = globalVariables.getGameVariables().perPlayerBalls * globalVariables.getGameVariables().numberOfPlayers;

                    globalVariables.getGameVariables().setBalls(true);
                    globalVariables.getGameVariables().setBats();

                    vibrator.vibrate(50);
                    Toast.makeText(HostActivity.this, "Verbindung zu" + Integer.toString(globalVariables.getSettingsVariables().numberOfPlayers - 1)
                            + " Spielern wird hergestellt", Toast.LENGTH_SHORT).show();

                    if(globalVariables.getSettingsVariables().setupConnectionState<2) {
                        globalVariables.getSettingsVariables().startAllClientThreads();
                        globalVariables.getSettingsVariables().setAllClientListeners(globalVariables.getClientListener());
                    }
                    globalVariables.getSettingsVariables().connectAllClients();
                    Log.d(TAG, "Connected to all clients.");

                    IGlobals.Ball[] tempBalls = new IGlobals.Ball[globalVariables.getGameVariables().numberOfBalls];

                    for (int i = 0; i < globalVariables.getGameVariables().numberOfBalls; i++) {
                        tempBalls[i] = globalVariables.getGameVariables().balls[i];
                    }

                    for (int i = 1; i < globalVariables.getSettingsVariables().numberOfPlayers; i++) {
                        Globals.SendVariables.SendSettings settings = new Globals.SendVariables.SendSettings();
                        settings.yourPlayerNumber = i;
                        settings.numberOfPlayers = globalVariables.getSettingsVariables().numberOfPlayers;
                        settings.ipAdresses = globalVariables.getSettingsVariables().ipAdresses.toArray(new String[0]);
                        settings.playerNames = globalVariables.getSettingsVariables().playerNames.toArray(new String[0]);

                        settings.balls = tempBalls;

                        settings.gameMode = globalVariables.getSettingsVariables().gameMode;
                        settings.gravityState = globalVariables.getGameVariables().gravityState;
                        settings.attractionState = globalVariables.getGameVariables().attractionState;

                        globalVariables.getSettingsVariables().clientThreads[i].addObjectToProtocolSendList(settings, "tcp");
                    }

                    globalVariables.getSettingsVariables().setupConnectionState = 2;
                    globalVariables.getSettingsVariables().clientConnectionStates[globalVariables.getSettingsVariables().myPlayerNumber] = 2;
                }
            }
        });
    }

    @Override
    protected void onDestroy() {

        serverListUpdateTask.cancel(true);
        Log.d(TAG, "onDestroy: UpdateTask canceled");
        final Globals globalVariables=(Globals) getApplication();
        Log.d(TAG, "onDestroy: updatethread interrupted");

        super.onDestroy();
    }

    /********* OTHER FUNCTIONS *********/


    protected String wifiIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endian if needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();
        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e("WIFIIP", "Unable to get host address.");
            //ipAddressString = null;
            ipAddressString = "192.168.43.1";
        }
        return ipAddressString;
    }

    boolean checkIfIp(String teststring) {
        if(teststring != null) {
            String[] parts = teststring.split("\\.");
            if (parts.length == 4) {
                return (true);
            }
        }
        return (false);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            Log.d(this.getClass().getName(), "back button pressed");
            Globals globalVariables = (Globals) getApplicationContext();
            globalVariables.getSettingsVariables().serverThread.shutdownServer();
            globalVariables.getSettingsVariables().shutdownAllClients();

        }
        return super.onKeyDown(keyCode, event);
    }


    /********* Thread Function - Searching IP and displaying *********/
    //Zeigt die IP Adresse an während dem Suchen
    class UpdateTask extends AsyncTask<Void,Void,Void>{

        Globals globalVariables = (Globals) getApplicationContext();
        //ArrayAdapter<String> serverListViewAdapter;
        MiscClasses.PlayerArrayAdapter serverListViewAdapter;
        final TextView myIpTextView = (TextView) findViewById(R.id.IPtextView);
        final AlertDialog.Builder makeDialog = new AlertDialog.Builder(HostActivity.this);
        final View mView = getLayoutInflater().inflate(R.layout.dialog_choose_agent,null,false);
        final ListView availableAgentsListView = (ListView) mView.findViewById(R.id.availableAgentsListView);
        final Button selfPlayButton = (Button) mView.findViewById(R.id.selfPlayButton);
        List<String> agentsList = new ArrayList<>();
        List<String> agentsNameList = new ArrayList<>();
        ArrayAdapter agentsAdapter =
                new ArrayAdapter<>(getApplication(),R.layout.item_textview, R.id.listViewtextView,agentsNameList);
        AlertDialog alertDialog;

        final UpdateTask updateTask = this;

        @Override
        protected void onPreExecute() {
            ListView serverListView = (ListView) findViewById(R.id.serverListView);
            serverListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            serverListViewAdapter = new MiscClasses.PlayerArrayAdapter(HostActivity.this,R.layout.item_choice_multiple,R.id.choiceMultipleTextView,globalVariables.getSettingsVariables().playerList);
            //serverListViewAdapter = new ClientPlayerArrayAdapter(HostActivity.this, R.layout.serverlistview_row, R.id.connectionCheckedTextView,globalVariables.getSettingsVariables().discoveryIpAdresses);

            serverListView.setAdapter(serverListViewAdapter);
            availableAgentsListView.setAdapter(agentsAdapter);
            makeDialog.setView(mView);
            alertDialog = makeDialog.create();


            selfPlayButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(getApplication(),GDXGameLauncher.class);
                    intent.putExtra("mode","normal");
                    intent.putExtra("agentmode",false);
                    alertDialog.dismiss();
                    startActivity(intent);
                    updateTask.cancel(true);
                    finish();
                }

            });

            availableAgentsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                    Intent intent = new Intent(getApplication(),GDXGameLauncher.class);
                    intent.putExtra("mode","normal");
                    intent.putExtra("agentmode",true);
                    intent.putExtra("agentname",agentsList.get(i));
                    alertDialog.dismiss();
                    startActivity(intent);
                    updateTask.cancel(true);
                    finish();


                }
            });

            //globalVariables.setSearchConnecState(true);


            serverListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    Log.d(TAG, "onItemClick: " + Integer.toString(i));
                    Log.d(TAG, "onItemClick: " + globalVariables.getSettingsVariables().discoveryIpAdresses.get(i));
                    CheckedTextView checkedTextView = (CheckedTextView) view;
                    globalVariables.getSettingsVariables().discoveryIsChecked.set(i,checkedTextView.isChecked());

                }
            });


        }

        @Override
        protected Void doInBackground(Void... voids) {

            //kryostuff--------------------------------------

            Log.d(TAG, "doInBackground: Anfang Suche");

            while(globalVariables.getSettingsVariables().setupConnectionState ==0 && !isCancelled()){
                //sendHostConnect();
                publishProgress();
                try {
                    Thread.currentThread().sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                globalVariables.getSettingsVariables().myIpAdress=wifiIpAddress(getApplicationContext());

                myIpTextView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (checkIfIp(globalVariables.getSettingsVariables().myIpAdress)) {
                            myIpTextView.setText("Deine IP-Adresse lautet: " + globalVariables.getSettingsVariables().myIpAdress);
                        }
                        else {
                            myIpTextView.setText("Unable to get Ip-Adress");
                        }
                    }
                });
            }


            Log.d(TAG, "doInBackground: Ende Suche");

            while(globalVariables.getSettingsVariables().setupConnectionState ==1 && !isCancelled()) {

            }

            while(!globalVariables.getSettingsVariables().checkAllClientConnectionStates(2) && !isCancelled()) {

            }

            IGlobals.SendVariables.SendConnectionState sendConnectionState=new IGlobals.SendVariables.SendConnectionState();
            sendConnectionState.myPlayerNumber=globalVariables.getSettingsVariables().myPlayerNumber;
            sendConnectionState.connectionState=3;
            globalVariables.getSettingsVariables().sendObjectToAllClients(sendConnectionState,"tcp");

            globalVariables.getSettingsVariables().clientConnectionStates[globalVariables.getSettingsVariables().myPlayerNumber] =3;

            if(!isCancelled()) {
                File agentsDir = new File(getApplication().getFilesDir().getAbsolutePath() + File.separator + "agents");
                String[] agentFiles = agentsDir.list();
                for(int i = 0; i<agentFiles.length;i++) {
                    String[] tempSplit1 = agentFiles[i].split("\\.");

                    String[] tempSplit2 = tempSplit1[0].split("_");
                    if(tempSplit2[2].equals(Integer.toString(globalVariables.getGameVariables().numberOfBalls))) {
                        agentsList.add(tempSplit1[0]);
                        agentsNameList.add(tempSplit2[0]+" (" + tempSplit2[3]+")");
                    }

                }
                agentsAdapter.notifyDataSetChanged();


                //serverListUpdateTask.cancel(true);


                //startActivity(new Intent(getApplicationContext(), GDXGameLauncher.class));
                //globalVariables.myThread.stop();

                //finish();

                Log.d(TAG, "Game started");
            } else {
                Log.d(TAG,"skipped do in background due to cancelling");
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            if(globalVariables.getSettingsVariables().updateListViewState) {
                serverListViewAdapter.notifyDataSetChanged();
                globalVariables.getSettingsVariables().updateListViewState=false;
                //
            }
        }

        @Override
        protected void onCancelled() {
            Log.d(TAG, "onCancelled: canceld");
            super.onCancelled();
        }

        @Override
        protected void onPostExecute(Void Void) {
            alertDialog.show();

            Log.d(TAG, "onPostExecute:  UpdateTask Abgeschlossen");

        }
        HostActivity m_activity = null;
    }


}