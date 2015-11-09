package org.cloudyna.smiles;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Toast;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Properties;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class MainActivity extends Activity {

    private static char[] PASSWORD = "".toCharArray();
    private static final String [] EPS = {"TLSv1.2"};
    private static final String URL_PROP = "url";
    private static final String CLIENT_ID_PROP = "clientId";
    private static final String TOPIC_PROP = "topic";
    private static final String LOG_TAG = "VoterApp";

    private Button singleButton;
    private Button doubleButton;

    private MqttClient client;
    private String androidId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            Properties props = new Properties();
            props.load(getResources().openRawResource(R.raw.client));
            String topic = props.getProperty(TOPIC_PROP);
            this.androidId = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            client = newMqttClient(props);
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
    protected void onStop() {
        try {
            super.onStop();
            client.disconnect();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error", e);
        }
    }

    private MqttClient newMqttClient(Properties props) throws Exception {
        String url = props.getProperty(URL_PROP);
        return new MqttClient(url, androidId, new MemoryPersistence());
    }

    private void openMqttConnection() throws Exception {
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setSocketFactory(new SSLSocketFactory() {

            private SSLSocketFactory sf = newSSLSocketFactory(MainActivity.this);

            @Override
            public Socket createSocket() throws IOException {
                SSLSocket ssl = (SSLSocket) sf.createSocket();
                ssl.setEnabledProtocols(EPS);
                return ssl;
            }


            @Override
            public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
                return sf.createSocket(address, port, localAddress, localPort);
            }

            @Override
            public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
                return sf.createSocket(host, port, localHost, localPort);
            }

            @Override
            public Socket createSocket(InetAddress host, int port) throws IOException {
                return sf.createSocket(host, port);
            }

            @Override
            public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
                return sf.createSocket(host, port);
            }

            @Override
            public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                return sf.createSocket(s, host, port, autoClose);
            }

            @Override
            public String[] getSupportedCipherSuites() {
                return sf.getSupportedCipherSuites();
            }

            @Override
            public String[] getDefaultCipherSuites() {
                return sf.getDefaultCipherSuites();
            }
        });
        client.connect(connOpts);
    }

    private SSLSocketFactory newSSLSocketFactory(Context ctx) throws Exception {
        X509Certificate ca = null;
        X509Certificate cert = null;
        KeyPair key = null;

        Security.addProvider(new BouncyCastleProvider());

        PEMReader caReader = null;
        PEMReader certReader = null;
        PEMReader pkReader = null;
        try {
            caReader = new PEMReader(new InputStreamReader(ctx.getResources().openRawResource(R.raw.ca)));
            certReader = new PEMReader(new InputStreamReader(ctx.getResources().openRawResource(R.raw.cert_pem)));
            pkReader = new PEMReader(new InputStreamReader(ctx.getResources().openRawResource(R.raw.private_key)),
                    new PasswordFinder() {
                        @Override
                        public char[] getPassword() {
                            return "".toCharArray();
                        }
                    });
            ca = (X509Certificate) caReader.readObject();
            cert = (X509Certificate) certReader.readObject();
            key = (KeyPair) pkReader.readObject();
        } finally {
            if (caReader != null) {
                caReader.close();
            }
            if (certReader != null) {
                certReader.close();
            }
            if (pkReader != null) {
                pkReader.close();
            }
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
        caKs.load(null, null);
        caKs.setCertificateEntry("ca-iot", ca);
        tmf.init(caKs);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("2c48fc6ff8-cert", cert);
        ks.setKeyEntry("2c48fc6ff8-private-key", key.getPrivate(), PASSWORD, new java.security.cert.Certificate[] { cert });
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASSWORD);
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context.getSocketFactory();
    }

    private class VotingButtonOnClickListener implements View.OnClickListener {

        private String MESSAGE_TEMPLATE = "{\"serialNumber\": \"$serialNumber\", \"batteryVoltage\": \"-1\", \"clickType\": \"$clickType\"}";

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
                            sendCloudSmile(androidId, smileType, topic, client);
                            Thread.sleep(1000);
                            MainActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(), "Thank you for your vote!", Toast.LENGTH_LONG).show();
                                    singleButton.setEnabled(true);
                                    doubleButton.setEnabled(true);
                                }
                            });
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Error while sending smile", e);
                        }
                        return null;
                    }
                }.execute();
                Animation shake = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.shake);
                v.startAnimation(shake);
        }

        private void sendCloudSmile(String serialNumber, SmileType st, String topic, MqttClient client) throws Exception {
            if (!client.isConnected()) {
                openMqttConnection();
            }
            MqttMessage message = new MqttMessage(
                    MESSAGE_TEMPLATE.replace("$serialNumber", serialNumber).replace("$clickType", st.getValue()).getBytes());
            client.publish(topic, message);
        }
    }
}
