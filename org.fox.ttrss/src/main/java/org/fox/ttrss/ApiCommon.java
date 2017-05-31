package org.fox.ttrss;

import android.os.Build;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Created by andrew on 31.05.2017.
 */

public class ApiCommon {
    public enum ApiError { NO_ERROR, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_NOT_FOUND,
        HTTP_SERVER_ERROR, HTTP_OTHER_ERROR, SSL_REJECTED, SSL_HOSTNAME_REJECTED, PARSE_ERROR, IO_ERROR, OTHER_ERROR, API_DISABLED,
        API_UNKNOWN, LOGIN_FAILED, INVALID_URL, API_INCORRECT_USAGE, NETWORK_UNAVAILABLE, API_UNKNOWN_METHOD }

    public static void trustAllHosts(boolean trustAnyCert, boolean trustAnyHost) {
        try {
            if (trustAnyCert) {
                X509TrustManager easyTrustManager = new X509TrustManager() {

                    public void checkClientTrusted(
                            X509Certificate[] chain,
                            String authType) throws CertificateException {
                        // Oh, I am easy!
                    }

                    public void checkServerTrusted(
                            X509Certificate[] chain,
                            String authType) throws CertificateException {
                        // Oh, I am easy!
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                };

                // Create a trust manager that does not validate certificate chains
                TrustManager[] trustAllCerts = new TrustManager[] {easyTrustManager};

                // Install the all-trusting trust manager

                SSLContext sc = SSLContext.getInstance("TLS");

                sc.init(null, trustAllCerts, new java.security.SecureRandom());

                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            }

            if (trustAnyHost) {
                HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    protected static void disableConnectionReuseIfNecessary() {
        // HTTP connection reuse which was buggy pre-froyo
        if (Integer.parseInt(Build.VERSION.SDK) < Build.VERSION_CODES.FROYO) {
            System.setProperty("http.keepAlive", "false");
        }
    }

}
