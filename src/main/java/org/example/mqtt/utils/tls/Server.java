package org.example.mqtt.utils.tls;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

/**
 * @author 罗涛
 * @title Server
 * @date 2020/10/13 18:50
 */
public class Server implements Runnable, HandshakeCompletedListener {
    public static final int SERVER_PORT = 11123;
    private final Socket s;
    private String peerCerName;
    public Server(Socket s) {
        this.s = s;
    }
    public static void main(String[] args) throws Exception {
        String serverKeyStoreFile = "F:\\code\\java\\open\\ca-cert\\java keytool\\server.jks";
        String serverKeyStorePwd = "123456";
        String ServerKeyPwd = "123456";
        String serverTrustKeyStoreFile = "F:\\code\\java\\open\\ca-cert\\java keytool\\root.jks";
        String serverTrustKeyStorePwd = "123456";
        /*
         * 加载server.keystore
         *
         */
        KeyStore serverKeyStore = KeyStore.getInstance("JKS");
        serverKeyStore.load(new FileInputStream(serverKeyStoreFile), serverKeyStorePwd.toCharArray());
        /*
         * 加载servertrust.keystore
         *
         */
        KeyStore serverTrustKeyStore = KeyStore.getInstance("JKS");
        serverTrustKeyStore.load(new FileInputStream(serverTrustKeyStoreFile), serverTrustKeyStorePwd.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverKeyStore, ServerKeyPwd.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(serverTrustKeyStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
        SSLServerSocket sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(SERVER_PORT);
        //设置双向验证
        sslServerSocket.setNeedClientAuth(true);

        while (true) {
            SSLSocket s = (SSLSocket)sslServerSocket.accept();
            Server cs = new Server(s);
            s.addHandshakeCompletedListener(cs);
            new Thread(cs).start();
        }
    }

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter writer = new PrintWriter(s.getOutputStream(), true);

            writer.println("Welcome~, enter exit to leave.");
            String message;
            while ((message = reader.readLine()) != null && !message.trim().equalsIgnoreCase("exit")) {
                writer.println("Echo: " + message);
            }
            writer.println("Bye~, " + peerCerName);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void handshakeCompleted(HandshakeCompletedEvent event) {
        try {
            X509Certificate cert = (X509Certificate) event.getPeerCertificates()[0];
            peerCerName = cert.getSubjectX500Principal().getName();
        } catch (SSLPeerUnverifiedException ex) {
            ex.printStackTrace();
        }
    }

}
