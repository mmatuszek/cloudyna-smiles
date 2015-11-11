package org.cloudyna.smiles.mqtt;

import android.content.Context;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.cloudyna.smiles.R;
import org.cloudyna.smiles.SmileType;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class SmilesMqttClient {

    private static char[] PASSWORD = "".toCharArray();
    private static final String [] EPS = {"TLSv1.2"};
    private String MESSAGE_TEMPLATE = "{\"serialNumber\": \"$serialNumber\", \"batteryVoltage\": \"-1\", \"clickType\": \"$clickType\"}";

    private MqttClient client;
    private Context ctx;

    public SmilesMqttClient(String url, String clientId, Context ctx) {
        try {
            this.ctx = ctx;
            client = new MqttClient(url, clientId, new MemoryPersistence());
        } catch (MqttException e) {
            throw new SmilesMqttClientException(e);
        }
    }

    public void connect() {
        try {
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setSocketFactory(new SSLSocketFactory() {

                private SSLSocketFactory sf = newSSLSocketFactory(ctx);

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
                public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                    return sf.createSocket(host, port, localHost, localPort);
                }

                @Override
                public Socket createSocket(InetAddress host, int port) throws IOException {
                    return sf.createSocket(host, port);
                }

                @Override
                public Socket createSocket(String host, int port) throws IOException {
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
        } catch (Exception e) {
            throw new SmilesMqttClientException(e);
        }
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
        ks.setCertificateEntry("cert", cert);
        ks.setKeyEntry("private-key", key.getPrivate(), PASSWORD, new java.security.cert.Certificate[] { cert });
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASSWORD);
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context.getSocketFactory();
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    public void disconnect() {
        try {
            client.disconnect();
        } catch (MqttException e) {
            throw new SmilesMqttClientException(e);
        }
    }

    public void publishMessage(String serialNumber, SmileType st, String topic) {
        try {
            if (!isConnected()) {
                connect();
            }
            MqttMessage message = new MqttMessage(
                    MESSAGE_TEMPLATE.replace("$serialNumber", serialNumber).replace("$clickType", st.getValue()).getBytes());
            client.publish(topic, message);
        } catch (Exception e) {
            throw new SmilesMqttClientException(e);
        }
    }
}
