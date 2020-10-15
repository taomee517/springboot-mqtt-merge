package org.example.mqtt.utils.tls;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.Socket;
import java.security.KeyStore;

/**
 * @author 罗涛
 * @title Client
 * @date 2020/10/13 18:52
 */
public class Client {
    public static void main(String[] args) throws Exception {
        String clientKeyStoreFile = "F:\\code\\java\\open\\ca-cert\\java keytool\\client.jks";
        String clientKeyStorePwd = "123456";
        String clientKeyPwd = "123456";
        String clientTrustKeyStoreFile = "F:\\code\\java\\open\\ca-cert\\java keytool\\root.jks";
        String clientTrustKeyStorePwd = "123456";
        //生成client的keystore对象
        KeyStore clientKeyStore = KeyStore.getInstance("JKS");
        clientKeyStore.load(new FileInputStream(clientKeyStoreFile), clientKeyStorePwd.toCharArray());
        //生成信任证书的keystore对象
        KeyStore clientTrustKeyStore = KeyStore.getInstance("JKS");
        clientTrustKeyStore.load(new FileInputStream(clientTrustKeyStoreFile), clientTrustKeyStorePwd.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(clientKeyStore, clientKeyPwd.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(clientTrustKeyStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        Socket socket = socketFactory.createSocket("localhost", Server.SERVER_PORT);

        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        send("hello", out);
        send("exit", out);
        receive(in);
        socket.close();
    }

    public static void send(String s, PrintWriter out) throws IOException {
        System.out.println("Sending: " + s);
        out.println(s);
    }

    public static void receive(BufferedReader in) throws IOException {
        String s;
        while ((s = in.readLine()) != null) {
            System.out.println("Reveived: " + s);
        }
    }
}
