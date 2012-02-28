package org.runetekk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Client.java
 * @version 1.0.0
 * @author RuneTekk Development (SiniSoul)
 */
public final class Client {
    
    /**
     * The maximum queue size.
     */
    public static final int QUEUE_SIZE = 20;
    
    /**
     * The time at which this client should be removed and destroyed. If
     * the clientTimeout is less than zero then a clientTimeout is not currently set 
     * for this client.
     */
    long clientTimeout;
    
    /**
     * The handshake for the client was handled to continue further communications.
     */
    boolean handshakeHandled;
    
    /**
     * The priority requests for this client.
     */
    int[] priorityRequests;
    
    /**
     * The urgent requests for this client.
     */
    int[] urgentRequests;
    
    /**
     * The passive requests for this client.
     */
    int[] passiveRequests;
    
    /**
     * The {@link InputStream} of this client.
     */
    InputStream inputStream;
    
    /**
     * The {@link OutputStream} of this client.
     */
    OutputStream outputStream;
    
    /**
     * Destroys this {@link Client}.
     */
    public void destroy() {
        try {
            inputStream.close();
            outputStream.close();
        } catch(IOException ioex) {}      
        priorityRequests = null;
        urgentRequests = null;
        passiveRequests = null;
    }
    
    /**
     * Constructs a new {@link Client};
     * @param socket The socket to create the client from.
     */
    public Client(Socket socket) throws IOException {
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
        priorityRequests = new int[QUEUE_SIZE + 3];
        urgentRequests = new int[QUEUE_SIZE + 3];
        passiveRequests = new int[QUEUE_SIZE + 3];
    }  
}
