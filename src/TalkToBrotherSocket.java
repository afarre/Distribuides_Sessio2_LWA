import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
public class TalkToBrotherSocket extends Thread{
    private static BufferedReader input = null;
    //private static SelectionKey key;

    // The selector we'll be monitoring
    private static Selector selector = null;

    // The channel on which we'll accept connections
    private ServerSocketChannel serverChannel;

    // The buffer into which we'll read data when it's available
    private ByteBuffer firstBuffer = ByteBuffer.allocate(1024);
    private ByteBuffer secondBuffer = ByteBuffer.allocate(1024);

    private S_LWA s_lwa;
    private NIOClient nioClient;

    private int firstRemoteSocketPort = -1;
    private int secondRemoteSocketPort = -1;
    private boolean firstHasResponded = false;
    private boolean secondHasResponded = false;
    private static SocketChannel firstClient;
    private static SocketChannel secondClient;

    private LamportRequest lamportRequest;

    private int clock;
    private int port;
    private int firstPort;
    private int secondPort;
    private int id;
    private String process;

    public TalkToBrotherSocket(S_LWA s_lwa, int clock, int port, int firstPort, int secondPort, int id, String process) {
        this.s_lwa = s_lwa;
        this.clock = clock;
        this.port = port;
        this.firstPort = firstPort;
        this.secondPort = secondPort;
        this.id = id;
        this.process = process;

        try {
            // Set NIO server
            setServer();
            System.out.println("Server configured.\n");

            // Set NIO clients

            /*connectClient(firstPort, true);
            registerClient(true);
            connectClient(secondPort, false);
            registerClient(false);
             */

            nioClient = new NIOClient(s_lwa, this, clock, firstPort, secondPort, id, process);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
/*
    private void connectClient(int port, boolean first) {
        try {
            if (first){
                System.out.println("Opening sockets to port " + port);
                firstClient = SocketChannel.open(new InetSocketAddress("localhost", port));
                firstBuffer = ByteBuffer.allocate(1024);
            }else {
                System.out.println("Opening sockets to port " + port);
                secondClient = SocketChannel.open(new InetSocketAddress("localhost", port));
                secondBuffer = ByteBuffer.allocate(1024);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void registerClient(boolean first) throws IOException {
        selector.select();
        SelectionKey key = selector.selectedKeys().iterator().next();
//        selector.selectedKeys().iterator().remove();

        if (!key.isValid()) {
            System.err.println("Invalid key. Exiting program.");;
            System.exit(0);
        }

        // Check if they key is ready to accept a new socket connection
        if (key.isAcceptable()) {
            System.out.println("\tACCEPT");
            //keyAccept(key, first);
        }
    }
*/
    @Override
    public void run() {
        // Create an event for the other brothers to read
        nioClient.identification();
        lamportRequest = nioClient.sendRequest();

        while (true) {
            try {
                // Wait for an event one of the registered channels
                selector.select();

                // Iterate over the set of keys for which events are available
                Iterator selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    // Check if they key is ready to accept a new socket connection
                    if (key.isAcceptable()) {
                        System.out.println("\nACCEPT");
                        keyAccept(key);

                    } else if (key.isReadable()){
                        System.out.println("\nREAD");
                        keyRead(key);
                    } else if (key.isWritable()){
                        System.out.println("\nWRITE");
                        keyWrite(key);
                        if ((int) key.attachment() == 1){
                            nioClient.read(1);
                        }else if ((int) key.attachment() == 2) {
                            nioClient.read(2);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void keyAccept(SelectionKey key /*, boolean first*/) throws IOException {
        // For an accept to be pending the channel must be a server socket channel.
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        // Accept the connection and make it non-blocking
        SocketChannel socketChannel = serverSocketChannel.accept();
        //Socket socket = socketChannel.socket();
        socketChannel.configureBlocking(false);
        Socket socket = socketChannel.socket();
        SocketAddress remoteAddr = socket.getRemoteSocketAddress();
        System.out.println("\tAccepted connection from " + remoteAddr);

        if (firstRemoteSocketPort == -1){
            firstRemoteSocketPort = socket.getPort();
            System.out.println("\tFirst remote socket port = " + firstRemoteSocketPort);
        }else {
            secondRemoteSocketPort = socket.getPort();
            System.out.println("\tSecond remote socket port = " + secondRemoteSocketPort);
        }

        // Register the new SocketChannel with our Selector, indicating
        // we'd like to be notified when there's data waiting to be read
        socketChannel.register(selector, SelectionKey.OP_READ);
    }

    private void keyRead(SelectionKey key) throws IOException {
        // Create a SocketChannel to read the request
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Socket socket = socketChannel.socket();
        SocketAddress remoteAddr = socket.getRemoteSocketAddress();
        System.out.println("\tReading from " + remoteAddr);

        if (socket.getPort() == firstRemoteSocketPort){
            System.out.println("\tReading from first remote socket port.");
            read(socketChannel, socket, key, firstBuffer);
        }else if (socket.getPort() == secondRemoteSocketPort) {
            System.out.println("\tReading from second remote socket port.");
            read(socketChannel, socket, key, secondBuffer);
        }
    }

    private void read(SocketChannel socketChannel, Socket socket, SelectionKey key, ByteBuffer buff) throws IOException {
        String msg = null;

        // Clear out our read buffer so it's ready for new data
        buff.clear();

        // Attempt to read off the channel
        int numRead;
        try {
            //buff.flip();  //make buffer ready for read
            //buff.compact(); //make buffer ready for writing
            numRead = socketChannel.read(buff);
        } catch (IOException e) {
            System.out.println("\tClosing socket");
            // The remote forcibly closed the connection, cancel the selection key and close the channel.
            key.cancel();
            socketChannel.close();
            return;
        }
        if (numRead == -1) {
            System.out.println("\tShutting down socket");
            // Remote entity shut the socket down cleanly. Do the same from our end and cancel the channel.
            key.channel().close();
            key.cancel();
            return;
        }

        msg = new String(buff.array()).trim();
        buff.clear();

        System.out.println("\t1. Read this msg: " + msg);

        if (msg.contains("ID") && msg.contains("LamportRequest")){
            String [] aux = msg.split("LamportRequest");
            //System.out.println("Pri: " + aux[0]);
            //System.out.println("Seg: " + aux[1]);
            String [] id = aux[0].split("#");
            if (id[1].equals("1")){
                System.out.println("Afegint a la key el numero: " + 1);
                key.attach(1);
            }else if (id[1].equals("2")){
                System.out.println("Afegint a la key el numero: " + 2);
                key.attach(2);
            }

            Gson gson = new Gson();
            LamportRequest lamportRequest = gson.fromJson(aux[1], LamportRequest.class);
            System.out.println("\t2. Parsed this request: " + lamportRequest);
            //TODO: Afegir a la cua de lamport
            key.interestOps(SelectionKey.OP_WRITE);

        }else if (msg.contains("ID")){
            String [] id = msg.split("#");
            if (id[1].equals("1")){
                System.out.println("Afegint a la key el numero: " + 1);
                key.attach(1);
            }else if (id[1].equals("2")){
                System.out.println("Afegint a la key el numero: " + 2);
                key.attach(2);
            }
        }else if (msg.contains("LamportRequest")){
            msg = msg.replace("LamportRequest", "");
            Gson gson = new Gson();
            LamportRequest lamportRequest = gson.fromJson(msg, LamportRequest.class);
            System.out.println("\t2. Parsed this request: " + lamportRequest);
            //TODO: Afegir a la cua de lamport
            key.interestOps(SelectionKey.OP_WRITE);

        }else if (msg.contains("ResponseRequest")){
            if (socket.getPort() == firstRemoteSocketPort){
                System.out.println("\tFirst response done");
                firstHasResponded = true;
            }else if (socket.getPort() == secondRemoteSocketPort){
                System.out.println("\tSecond response done");
                secondHasResponded = true;
            }

            if (firstHasResponded && secondHasResponded){
                System.out.println("Got both responses so I must check if I can use screen.");
                //TODO: Notify que ya podem consultar CS
            }
        }
    }

    private void keyWrite(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Socket socket = socketChannel.socket();
        SocketAddress remoteAddr = socket.getRemoteSocketAddress();
        System.out.println("\tWriting to " + remoteAddr);

        //LamportRequest lamportRequest = new LamportRequest(clock, process, id);
        String msg = lamportRequest.toString();
        msg = msg.replace("LamportRequest",  "ResponseRequest");
        System.out.println("\tWritting: " + msg);

        int writenBytes = -1;
        if (socket.getPort() == firstRemoteSocketPort){
            System.out.println("Efectivament, el meu socket port: " + socket.getPort() + " es correspon amb el socket port del primer client: " + firstRemoteSocketPort + " que resulte que es el client numero : " + key.attachment().toString());
            // Clear out our read buffer so it's ready for new data
            firstBuffer.clear();
            firstBuffer = ByteBuffer.wrap(msg.getBytes());
            writenBytes = socketChannel.write(firstBuffer);
        }else if (socket.getPort() == secondRemoteSocketPort){
            System.out.println("Efectivament, el meu socket port: " + socket.getPort() + " es correspon amb el socket port del primer client: " + secondRemoteSocketPort + " que resulte que es el client numero : " + key.attachment().toString());
            // Clear out our read buffer so it's ready for new data
            secondBuffer.clear();
            secondBuffer = ByteBuffer.wrap(msg.getBytes());
            writenBytes = socketChannel.write(secondBuffer);
        }else {
            System.err.println("Current port does not match any of the client's ports. Exiting program.");
            System.exit(0);
        }

        System.out.println("\tI wrote " + writenBytes + " bytes.");
        // We wrote away all data, so we're no longer interested
        // in writing on this socket. Switch back to waiting for data.
        key.interestOps(SelectionKey.OP_READ);
    }

    private void setServer() throws IOException {
        // Create a new selector
        selector = Selector.open();

        // Create a new non-blocking server socket channel
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);

        // Bind the server socket to the specified address and port
        serverChannel.bind(new InetSocketAddress("localhost", port));

        // Register the server socket channel, indicating an interest in
        // accepting new connections
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void releaseProcess(LamportRequest executedRequest) {
        nioClient.releaseProcess(executedRequest);
    }

    public void emptyBuffer() {
        firstBuffer.clear();
    }

    public int getFirstRemoteSocketPort() {
        return firstRemoteSocketPort;
    }

    public int getSecondRemoteSocketPort() {
        return secondRemoteSocketPort;
    }
}