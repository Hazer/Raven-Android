package com.joshdholtz.sentry;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by Hazer on 2/25/16.
 */
class DSN {
//    private static final String DEFAULT_BASE_URL = "https://app.getsentry.com";

    private final URI hostURI;
    //    private ArrayList<String> protocolSettings;
    private String path;
    private String projectId;
    private String host;
    private int port;
    private String protocol;
    private String publicKey;
    private String secretKey;

    public DSN(String dsnString) {
        if (dsnString == null)
            throw new InvalidDsnException("The sentry DSN must be provided and not be null");

        URI dsn;
        try {
            dsn = new URI(dsnString);
        } catch (URISyntaxException e) {
            throw new InvalidDsnException("Impossible to determine Sentry's URI from the DSN '" + dsnString + "'", e);
        }

        extractProtocolInfo(dsn);
        extractUserKeys(dsn);
        extractHostInfo(dsn);
        extractPathInfo(dsn);
//        extractOptions(dsn);

        validate();

        hostURI = makeHostURI(dsn);
    }

    private URI makeHostURI(URI dsn) {
        try {
            return new URI(protocol, null, host, port, path, null, null);
        } catch (URISyntaxException e) {
            throw new InvalidDsnException("Impossible to determine Sentry's URI from the DSN '" + dsn + "'", e);
        }
    }

    /**
     * Extracts the path and the project ID from the DSN provided as an {@code URI}.
     *
     * @param dsnUri DSN as an URI.
     */
    private void extractPathInfo(URI dsnUri) {
        String uriPath = dsnUri.getPath();
        if (uriPath == null)
            return;
        int projectIdStart = uriPath.lastIndexOf("/") + 1;
        path = uriPath.substring(0, projectIdStart);
        projectId = uriPath.substring(projectIdStart);
    }

    /**
     * Extracts the hostname and port of the Sentry server from the DSN provided as an {@code URI}.
     *
     * @param dsnUri DSN as an URI.
     */
    private void extractHostInfo(URI dsnUri) {
        host = dsnUri.getHost();
        port = dsnUri.getPort();
    }

    /**
     * Extracts the scheme and additional protocol options from the DSN provided as an {@code URI}.
     *
     * @param dsnUri DSN as an URI.
     */
    private void extractProtocolInfo(URI dsnUri) {
        String scheme = dsnUri.getScheme();
        if (scheme == null)
            return;
        String[] schemeDetails = scheme.split("\\+");
//        protocolSettings.addAll(Arrays.asList(schemeDetails).subList(0, schemeDetails.length - 1));
        protocol = schemeDetails[schemeDetails.length - 1];
    }

    /**
     * Extracts the public and secret keys from the DSN provided as an {@code URI}.
     *
     * @param dsnUri DSN as an URI.
     */
    private void extractUserKeys(URI dsnUri) {
        String userInfo = dsnUri.getUserInfo();
        if (userInfo == null)
            return;
        String[] userDetails = userInfo.split(":");
        publicKey = userDetails[0];
        if (userDetails.length > 1)
            secretKey = userDetails[1];
    }
//
//    /**
//     * Extracts the DSN options from the DSN provided as an {@code URI}.
//     *
//     * @param dsnUri DSN as an URI.
//     */
//    private void extractOptions(URI dsnUri) {
//        String query = dsnUri.getQuery();
//        if (query == null || query.isEmpty())
//            return;
//        for (String optionPair : query.split("&")) {
//            try {
//                String[] pairDetails = optionPair.split("=");
//                String key = URLDecoder.decode(pairDetails[0], "UTF-8");
//                String value = pairDetails.length > 1 ? URLDecoder.decode(pairDetails[1], "UTF-8") : null;
//                options.put(key, value);
//            } catch (UnsupportedEncodingException e) {
//                throw new IllegalArgumentException("Impossible to decode the query parameter '" + optionPair + "'", e);
//            }
//        }
//    }

    /**
     * Validates internally the DSN, and check for mandatory elements.
     * <p/>
     * Mandatory elements are the {@link #host}, {@link #publicKey}, {@link #secretKey} and {@link #projectId}.
     */
    private void validate() {
        String missingElements = "";
        if (host == null)
            missingElements += "," + "host";
        if (publicKey == null)
            missingElements += "," + "public key";
        if (secretKey == null)
            missingElements += "," + "secret key";
        if (projectId == null || projectId.isEmpty())
            missingElements += "," + "project ID";

        if (!missingElements.isEmpty())
            throw new InvalidDsnException("Invalid DSN, the following properties aren't set '" + missingElements + "'");
    }


    public String getHost() {
        return host;
    }

    public URI getHostURI() {
        return hostURI;
    }

    public String getPath() {
        return path;
    }

    public int getPort() {
        return port;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    @Override
    public int hashCode() {
        int result = publicKey.hashCode();
        result = 31 * result + projectId.hashCode();
        result = 31 * result + host.hashCode();
        result = 31 * result + port;
        result = 31 * result + path.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Dsn{"
                + "uri=" + hostURI
                + '}';
    }

    private class InvalidDsnException extends RuntimeException {
        public InvalidDsnException(String message, Exception cause) {
            super(message, cause);
        }

        public InvalidDsnException(String message) {
            super(message);
        }
    }
}
