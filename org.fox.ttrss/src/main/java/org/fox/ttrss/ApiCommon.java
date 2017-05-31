package org.fox.ttrss;

import android.os.Build;
import android.util.Log;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class ApiCommon {
    public static final String TAG = "ApiCommon";

    public enum ApiError { NO_ERROR, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_NOT_FOUND,
        HTTP_SERVER_ERROR, HTTP_OTHER_ERROR, SSL_REJECTED, SSL_HOSTNAME_REJECTED, PARSE_ERROR, IO_ERROR, OTHER_ERROR, API_DISABLED,
        API_UNKNOWN, LOGIN_FAILED, INVALID_URL, API_INCORRECT_USAGE, NETWORK_UNAVAILABLE, API_UNKNOWN_METHOD }

    public static int getErrorMessage(ApiError error) {
        switch (error) {
            case NO_ERROR:
                return R.string.error_unknown;
            case HTTP_UNAUTHORIZED:
                return R.string.error_http_unauthorized;
            case HTTP_FORBIDDEN:
                return R.string.error_http_forbidden;
            case HTTP_NOT_FOUND:
                return R.string.error_http_not_found;
            case HTTP_SERVER_ERROR:
                return R.string.error_http_server_error;
            case HTTP_OTHER_ERROR:
                return R.string.error_http_other_error;
            case SSL_REJECTED:
                return R.string.error_ssl_rejected;
            case SSL_HOSTNAME_REJECTED:
                return R.string.error_ssl_hostname_rejected;
            case PARSE_ERROR:
                return R.string.error_parse_error;
            case IO_ERROR:
                return R.string.error_io_error;
            case OTHER_ERROR:
                return R.string.error_other_error;
            case API_DISABLED:
                return R.string.error_api_disabled;
            case API_UNKNOWN:
                return R.string.error_api_unknown;
            case API_UNKNOWN_METHOD:
                return R.string.error_api_unknown_method;
            case LOGIN_FAILED:
                return R.string.error_login_failed;
            case INVALID_URL:
                return R.string.error_invalid_api_url;
            case API_INCORRECT_USAGE:
                return R.string.error_api_incorrect_usage;
            case NETWORK_UNAVAILABLE:
                return R.string.error_network_unavailable;
            default:
                Log.d(TAG, "getErrorMessage: unknown error code=" + error);
                return R.string.error_unknown;
        }

    }

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
