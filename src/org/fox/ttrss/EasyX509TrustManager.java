
package org.fox.ttrss;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

// http://stackoverflow.com/questions/6989116/httpget-not-working-due-to-not-trusted-server-certificate-but-it-works-with-ht

public class EasyX509TrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException { }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException { }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
        }

}
