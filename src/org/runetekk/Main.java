package org.runetekk;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main.java
 * @version 1.0.0
 * @author RuneTekk Development (SiniSoul)
 */
public final class Main implements Runnable {
    
    /**
     * The {@link Logger} utility.
     */
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    
    /**
     * The amount of bytes in a block.
     */
    private static final int BLOCK_SIZE = 500;
    
    /**
     * The amount of bytes in a request.
     */
    private static final int REQUEST_SIZE = 4;
        
    /**
     * The {@link DirectBuffer} array for all the loaded archives.
     */
    private static DirectBuffer[][] archiveBuffers;
    
    /**
     * The {@link FileIndex} array.
     */
    private static FileIndex[] fileIndexes;
    
    /**
     * The {@link Main} handler.
     */
    private static Main main;
    
    /**
     * The local thread.
     */
    private Thread thread;
    
    /**
     * The local thread is currently paused.
     */
    private boolean isPaused;
    
    /**
     * The {@link ServerSocket} to accept connections from.
     */
    private ServerSocket serverSocket;
    
    /**
     * The {@link Client} queue.
     */
    private Deque<Client> clientQueue;
    
    /**
     * Prints the application tag.
     */
    private static void printTag() {
        System.out.println(""
        + "                     _____               _______   _    _                           "
        + "\n                    |  __ \\             |__   __| | |  | |                       "
        + "\n                    | |__) |   _ _ __   ___| | ___| | _| | __                     "
        + "\n                    |  _  / | | | '_ \\ / _ \\ |/ _ \\ |/ / |/ /                  "
        + "\n                    | | \\ \\ |_| | | | |  __/ |  __/   <|   <                    "
        + "\n                    |_|  \\_\\__,_|_| |_|\\___|_|\\___|_|\\_\\_|\\_\\             "
        + "\n----------------------------------------------------------------------------------"
        + "\n                              Ondemand Server 1.0.0                               "
        + "\n                                 See RuneTekk.com                                 "
        + "\n                               Created by SiniSoul                                "
        + "\n----------------------------------------------------------------------------------");
    }
     
    @Override
    public void run() {
        for(;;) {
            if(isPaused)
                break;
            Client client = null;
            try {
                 Socket socket = serverSocket.accept();
                 client = new Client(socket);
            } catch(IOException ex) {
                if(!(ex instanceof SocketTimeoutException))
                    destroy();
            }     
            if(client != null) {   
                client.clientTimeout = System.currentTimeMillis() + 5000L;
                synchronized(clientQueue) {
                    clientQueue.add(client);
                }
            }   
            Client firstClient = null;
            clientloop:
            for(int i = 0; i < 10; i++) {
                client = clientQueue.poll();
                if(client == null)
                    break;
                if(firstClient == null)
                    firstClient = client;
                else if(firstClient != client) {
                    clientQueue.addLast(client);
                    break;
                }
                if(client.clientTimeout > 0L && client.clientTimeout < System.currentTimeMillis()) {
                    LOGGER.log(Level.WARNING, "Client disconnected : Timeout!");
                    client.destroy();
                    continue;
                }
                try {
                    int avail = client.inputStream.available();
                    if(!client.handshakeHandled) {
                        if(avail >= 1) {
                            if(client.inputStream.read() == 15) {
                                client.outputStream.write(new byte[8]);
                                client.outputStream.flush();
                                client.handshakeHandled = true;
                                client.clientTimeout = -1L;
                            } else {
                                LOGGER.log(Level.WARNING, "Client disconnected : Invalid OP!");
                                client.destroy();
                                continue;
                            }  
                        }
                    } else {
                        if(avail > REQUEST_SIZE) {
                            byte[] requestBuffer = new byte[avail - (avail % REQUEST_SIZE)];
                            int read;
                            for(int off = 0; off < avail; off += read) {
                                read = client.inputStream.read(requestBuffer, 0, requestBuffer.length - off);
                                if(read < 0)
                                    throw new IOException("EOF");
                            }
                            for(int off = 0; off < requestBuffer.length;) {
                                long hash = ((requestBuffer[off++] & 0xFF) << 24L) |
                                           ((requestBuffer[off++] & 0xFF) << 16L) |
                                           ((requestBuffer[off++] & 0xFF) << 8L) |
                                            (requestBuffer[off++] & 0xFF);
                                int archiveId = (int) (hash & 0xFFFF00) >> 8;
                                int indexId = (int) (hash & 0xFF000000) >> 24;
                                if(archiveBuffers.length > indexId && indexId >= 0 && archiveBuffers[indexId].length > archiveId && archiveId >= 0 && archiveBuffers[indexId][archiveId] != null) {
                                    hash |= (long) (archiveBuffers[indexId][archiveId].getPayload().length & 0xFFFF) << 32L;
                                } 
                                long[] queue = (hash & 0xFFL) == 2 ? client.urgentRequests : 
                                               (hash & 0xFFL) == 1 ? client.priorityRequests 
                                                                 : client.passiveRequests;
                                int writePosition = (int) queue[queue.length - 1];
                                queue[queue.length - 1] = (queue[queue.length - 1] + 1L) % Client.QUEUE_SIZE;
                                if(queue[queue.length - 1] == queue[queue.length - 2]) {
                                    LOGGER.log(Level.WARNING, "Client disconnected : Queue overfill!");
                                    client.destroy();
                                    continue clientloop;
                                }
                                queue[writePosition] = hash;
                            }
                        }
                        long[] queue = null;
                        int position = -1;
                        if(client.urgentRequests[client.urgentRequests.length - 1] != 
                           client.urgentRequests[client.urgentRequests.length - 2]) {
                            position = (int) client.urgentRequests[client.urgentRequests.length - 2];                          
                            queue = client.urgentRequests;
                        }   
                        if(position == -1) {
                            if(client.priorityRequests[client.priorityRequests.length - 1] != 
                               client.priorityRequests[client.priorityRequests.length - 2]) {
                                position = (int) client.priorityRequests[client.priorityRequests.length - 2];                               
                                queue = client.priorityRequests;
                            }  
                        }
                        if(position == -1) {
                            if(client.passiveRequests[client.passiveRequests.length - 1] != 
                               client.passiveRequests[client.passiveRequests.length - 2]) {
                                position = (int) client.passiveRequests[client.passiveRequests.length - 2];                               
                                queue = client.passiveRequests;
                            } 
                        }
                        if(queue != null) {
                            client.clientTimeout = -1L;
                            long hash = queue[position];
                            int archiveId = (int) (hash & 0xFFFF00) >> 8;
                            int indexId = (int) (hash & 0xFF000000) >> 24;
                            int size = (int) ((hash & 0xFFFF00000000L) >> 32L);
                            int block = (int) ((hash & 0xFF000000000000L) >> 48L);
                            byte[] header = { (byte) indexId, 
                                              (byte) (archiveId >> 8), 
                                              (byte) (archiveId & 0xFF),
                                              (byte) (size >> 8),
                                              (byte) (size & 0xFF),
                                              (byte) (block & 0xFF)
                                            };
                           client.outputStream.write(header);
                           int write = size - (block * BLOCK_SIZE); 
                           if(write > BLOCK_SIZE)
                               write = BLOCK_SIZE;
                           if(write > 0) {
                               client.outputStream.write(archiveBuffers[indexId][archiveId].get(BLOCK_SIZE * block, write), 0, write);
                               client.outputStream.flush();
                           }
                           if(write < BLOCK_SIZE) {
                               queue[queue.length - 2] = (queue[queue.length - 2] + 1) % Client.QUEUE_SIZE;
                           } else
                               queue[position] = (hash & ~0xFF000000000000L) | ((long) (block + 1) << 48L);               
                        } else {
                            if(client.clientTimeout < 0L)
                                client.clientTimeout = System.currentTimeMillis() + 5000L;
                        }
                    }         
                } catch(IOException ioex) {
                    LOGGER.log(Level.WARNING, "Error - ", ioex);
                    client.destroy();
                    continue;
                }
                clientQueue.addLast(client);
            }
        }
    }
    
    /**
     * Initializes the local thread.
     */
    private void initialize() {
        thread = new Thread(this);
        thread.start();
    }
    
    /**
     * Destroys this local application.
     */
    private void destroy() {
        if(!isPaused)  {
            if(thread != null) {
                synchronized(this) {
                    isPaused = true;
                    notifyAll();
                }
                try {
                    thread.join();
                } catch(InterruptedException ex) {
                }
            }
            thread = null;
        }
    }
    
    /**
     * The main starting point for this application.
     * @param args The command line arguments.
     */
    public static void main(String[] args) {
        args = args.length == 0 ? new String[] { "server", "server.properties" } : args;
        if(!args[0].equals("setup") && !args[0].equals("server")) {
            LOGGER.log(Level.SEVERE, "Invalid application mode : {0}!", args[0]);
            throw new RuntimeException();
        }
        printTag();
        Properties serverProperties = new Properties();
        try {
            serverProperties.load(new FileReader(args[1]));
        } catch(Exception ex) {
            LOGGER.log(Level.SEVERE, "Exception caught while loading the properties - ", ex);
            throw new RuntimeException();
        }
        String outDir = serverProperties.getProperty("OUTDIR");
        if(outDir == null) {
            LOGGER.log(Level.SEVERE, "OUTDIR property key is null!");
            throw new RuntimeException();
        }
        String qLoadFile = serverProperties.getProperty("LOADFILE");
        if(qLoadFile == null) {
            LOGGER.log(Level.SEVERE, "LOADFILE property key is null!");
            throw new RuntimeException();
        }
        String cacheDir = serverProperties.getProperty("CACHEDIR");
        if(cacheDir == null) {
            LOGGER.log(Level.SEVERE, "CACHEDIR property key is null!");
            throw new RuntimeException();
        }
        String mainIndexName = serverProperties.getProperty("MAINFILE");
        if(cacheDir == null) {
            LOGGER.log(Level.SEVERE, "MAINFILE property key is null!");
            throw new RuntimeException();
        }
        int maximumIndex = 0;
        int[] indexIds = null;
        try {
            String[] array = serverProperties.getProperty("INDEXIDS").split("[:]");
            indexIds = new int[array.length];
            for(int i = 0; i < array.length; i++) {
                indexIds[i] = Integer.parseInt(array[i]);
                if(indexIds[i] > maximumIndex)
                    maximumIndex = indexIds[i];
            }
        } catch(Exception ex) {
            LOGGER.log(Level.SEVERE, "Exception caught while loading the index ids - ", ex);
            throw new RuntimeException();
        }    
        String[] indexNames = new String[indexIds.length];
        for(int i = 0; i < indexIds.length; i++) {
            indexNames[i] = serverProperties.getProperty("INDEX-" + indexIds[i]);
            if(indexNames[i] == null) {
                LOGGER.log(Level.SEVERE, "INDEX-{0} property key is null!", i);
                throw new RuntimeException();
            }
        }
        RandomAccessFile mainFile = null;
        try {
            mainFile = new RandomAccessFile(cacheDir + mainIndexName, "r");
            fileIndexes = new FileIndex[maximumIndex + 1];
            for(int i = 0; i < indexNames.length; i++) {
                fileIndexes[indexIds[i]] = new FileIndex(indexIds[i] + 2, mainFile, new RandomAccessFile(cacheDir + indexNames[i], "r"));
            }
        } catch(Exception ex) {
            LOGGER.log(Level.SEVERE, "Exception thrown while loading the file indexes - ", ex);
            throw new RuntimeException();
        }
        if(args[0].equals("setup")) {
            try {
                DataOutputStream os = new DataOutputStream(new FileOutputStream(outDir + qLoadFile));
                for(int i = 0; i < indexIds.length; i++) { 
                    FileIndex index = fileIndexes[indexIds[i]];
                    int size = index.getSize();
                    for(int j = 0; j < size; j++) {
                        if(index.get(j) != null) {
                            os.writeByte(1);
                            os.writeByte(indexIds[i]);
                            os.writeShort(j);
                        }
                    }
                }
                os.writeByte(0);
                os.flush();
                os.close();
            } catch(Exception ex) {
                LOGGER.log(Level.SEVERE, "Exception caught while creating the LOADFILE - ", ex);
                throw new RuntimeException();
            }
        } else if(args[0].equals("server")) {
            int portOff = -1;
            try {
                portOff = Integer.parseInt(serverProperties.getProperty("PORTOFF"));
            } catch(Exception ex) {
                LOGGER.log(Level.SEVERE, "Exception caught while loading the port offset - ", ex);
                throw new RuntimeException();
            }
            serverProperties = null;
            archiveBuffers = new DirectBuffer[maximumIndex + 1][];
            try {
                DataInputStream is = new DataInputStream(new FileInputStream(outDir + qLoadFile));
                int opcode = 0;
                while((opcode = is.read()) != 0) {
                    if(opcode == 1) {
                        int indexId = is.read();
                        int archiveId = is.readShort();
                        if(archiveBuffers[indexId] == null)
                            archiveBuffers[indexId] = new DirectBuffer[fileIndexes[indexId].getSize()];
                        DirectBuffer buffer = archiveBuffers[indexId][archiveId] = new DirectBuffer();
                        buffer.put(fileIndexes[indexId].get(archiveId));
                    }
                }
                is.close();
            } catch(Exception ex) {
                LOGGER.log(Level.SEVERE, "Exception caught while loading the LOADFILE - ", ex);
                throw new RuntimeException();
            }
            main = new Main(portOff);
        }
    }  
    
    /**
     * Prevent external construction;
     * @param portOff The port offset to initialize the server on.
     */
    private Main(int portOff) {
        try {
            clientQueue = new LinkedList<Client>();
            serverSocket = new ServerSocket();
            serverSocket.setSoTimeout(5);
            serverSocket.bind(new InetSocketAddress(43594 + portOff));
            initialize();
        } catch(Exception ex) {
            LOGGER.log(Level.SEVERE, "Exception thrown while initializing : {0}", ex);
            throw new RuntimeException();
        }
    }
}
