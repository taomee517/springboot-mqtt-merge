package org.example.mqtt.utils.tls;

import io.netty.handler.ssl.SslContext;

import javax.net.ssl.*;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Objects;

/**
 * @author 罗涛
 * @title SslContextUtil
 * @date 2020/11/2 13:27
 */
public class SslContextUtil {
    public static SSLContext getServerContext(String serverCertPath, String serverCertPwd,
                                              String trustCertPath, String trustCertPwd) throws Exception{
        SSLContext serverSslContext = SSLContext.getInstance("SSL");
        KeyManager[] keyManagers = getKeyManagers(serverCertPath, serverCertPwd);
        TrustManager[] trustManagers = getTrustManagers(trustCertPath, trustCertPwd);
        serverSslContext.init(keyManagers, trustManagers, null);
        return serverSslContext;
    }

    public static SSLContext getClientContext(String clientCertPath, String clientCertPwd,
                                              String trustCertPath, String trustCertPwd) throws Exception{
        SSLContext clientSslContext = SSLContext.getInstance("SSL");
        KeyManager[] keyManagers = getKeyManagers(clientCertPath, clientCertPwd);
        TrustManager[] trustManagers = getTrustManagers(trustCertPath, trustCertPwd);
        clientSslContext.init(keyManagers, trustManagers, null);
        return clientSslContext;
    }


    public static KeyManager[] getKeyManagers(String certPath, String certPwd) throws Exception{
        KeyStore keyStore = getKeyStore(certPath, certPwd);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, certPwd.toCharArray());
        KeyManager[] keyManagers = kmf.getKeyManagers();
        return keyManagers;
    }



    public static TrustManager[] getTrustManagers(String certPath, String certPwd) throws Exception{
        KeyStore keyStore = getKeyStore(certPath, certPwd);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(keyStore);
        return tmf.getTrustManagers();
    }


    private static KeyStore getKeyStore(String certPath, String certPwd) throws Exception{
        InputStream is = SslContextUtil.class.getResourceAsStream(certPath);
        if(Objects.isNull(is)){
            throw new Exception("找不到数字证书");
        }
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(is, certPwd.toCharArray());
        is.close();
        return keyStore;
    }
}
