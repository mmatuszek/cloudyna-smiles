package org.cloudyna.smiles;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Toast;

import org.cloudyna.smiles.mqtt.SmilesMqttClient;

import java.util.Properties;

public class MainActivity extends Activity {

    private static final String URL_PROP = "url";
    private static final String CLIENT_ID_PROP = "clientId";
    private static final String TOPIC_PROP = "topic";
    private static final String LOG_TAG = "CloudynaSmilesApp";

    private Button singleButton;
    private Button doubleButton;

    private SmilesMqttClient client;
    private String androidId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            Properties props = new Properties();
            props.load(getResources().openRawResource(R.raw.client));
            String topic = props.getProperty(TOPIC_PROP);
            String url = props.getProperty(URL_PROP);
            String clientId = props.getProperty(CLIENT_ID_PROP);
            this.androidId = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            client = new SmilesMqttClient(url, clientId, this);
            singleButton = (Button) findViewById(R.id.single_button);
            doubleButton = (Button) findViewById(R.id.double_button);
            singleButton.setOnClickListener(new VotingButtonOnClickListener(SmileType.LIKE, topic));
            doubleButton.setOnClickListener(new VotingButtonOnClickListener(SmileType.LOVE, topic));
            Button infoButton = (Button) findViewById(R.id.info);
            infoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(getApplicationContext(), "Android ID: " + androidId, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error", e);
            Toast.makeText(getApplicationContext(), e.getCause().getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        client.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        client.disconnect();
    }

    private class VotingButtonOnClickListener implements View.OnClickListener {

        private SmileType smileType;
        private String topic;

        VotingButtonOnClickListener(SmileType smileType, String topic) {
            this.smileType = smileType;
            this.topic = topic;
        }

        @Override
        public void onClick(View v) {
                singleButton.setEnabled(false);
                doubleButton.setEnabled(false);
                new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... params) {
                        try {
                            client.publishMessage(androidId, smileType, topic);
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Error while sending smile", e);
                        }
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Thank you for your vote!", Toast.LENGTH_LONG).show();
                                singleButton.setEnabled(true);
                                doubleButton.setEnabled(true);
                            }
                        });
                        return null;
                    }
                }.execute();
                Animation shake = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.shake);
                v.startAnimation(shake);
        }
    }
}
