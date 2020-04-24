import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
public class TalkToBrotherSocket extends Thread{
    private static BufferedReader input = null;
    //private static SelectionKey key;


    private static final String POISON_PILL = "POISON_PILL";

    // The selector we'll be monitoring
    private static Selector selector = null;

    // The channel on which we'll accept connections
    private ServerSocketChannel serverChannel;

    // The buffer into which we'll read data when it's available
    private ByteBuffer buffer = ByteBuffer.allocate(1024);


    private NIOClient nioClient;

    private int clock;
    private int port;
    private int firstPort;
    private int secondPort;
    private int id;
    private String process;

    public TalkToBrotherSocket(int clock, int port, int firstPort, int secondPort, int id, String process) {
        this.clock = clock;
        this.port = port;
        this.firstPort = firstPort;
        this.secondPort = secondPort;
        this.id = id;
        this.process = process;

        try {
            setServer();
            System.out.println("Server configured.\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        nioClient = new NIOClient(clock, firstPort, secondPort, id, process);
    }

    @Override
    public void run() {
        while (true) {
            try {
                nioClient.sendRequests();
                // Wait for an event one of the registered channels
                selector.select();
               // System.out.println("Selected");

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
                        keyAccept(key);
                        System.out.println("Key accepted");
                    } else if (key.isReadable()){
                        System.out.println("Reading data from server");
                        keyRead(key);
                    } else if (key.isWritable()){
                        System.out.println("Writting data from server");
                        keyWrite(key);
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void keyWrite(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        String msg = "wakanda";
        buffer = ByteBuffer.wrap(msg.getBytes());
        socketChannel.write(buffer);
        // We wrote away all data, so we're no longer interested
        // in writing on this socket. Switch back to waiting for data.
        key.interestOps(SelectionKey.OP_READ);
    }

    private void keyRead(SelectionKey key) throws IOException {
        // Create a SocketChannel to read the request
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // Clear out our read buffer so it's ready for new data
        buffer.clear();
        //Arrays.fill(readBuffer.array(), (byte) 0);

        // Attempt to read off the channel
        int numRead;
        try {
            numRead = socketChannel.read(buffer);
        } catch (IOException e) {
            System.out.println("Closing socket");
            // The remote forcibly closed the connection, cancel
            // the selection key and close the channel.
            key.cancel();
            socketChannel.close();
            return;
        }

        if (numRead == -1) {
            System.out.println("Shutting down socket");
            // Remote entity shut the socket down cleanly. Do the
            // same from our end and cancel the channel.
            key.channel().close();
            key.cancel();
            return;
        }

        System.out.println("I read: " + new String(buffer.array()).trim());

        //key.interestOps(SelectionKey.OP_WRITE);

        // Hand the data off to our worker thread
        //this.worker.processData(this, socketChannel, this.buffer.array(), numRead);
    }

    private void keyAccept(SelectionKey key) throws IOException {
        // For an accept to be pending the channel must be a server socket channel.
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        // Accept the connection and make it non-blocking
        SocketChannel socketChannel = serverSocketChannel.accept();
        //Socket socket = socketChannel.socket();
        socketChannel.configureBlocking(false);

        // Register the new SocketChannel with our Selector, indicating
        // we'd like to be notified when there's data waiting to be read
        socketChannel.register(selector, SelectionKey.OP_READ);
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
/*
    public void makeRequest(String process, int id) {
        try {
            LamportRequest lamportRequest = new LamportRequest(clock, process, id);
            System.out.println("Sending this request: " + lamportRequest.toString());
            keyWritable(key, lamportRequest.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

 */
}