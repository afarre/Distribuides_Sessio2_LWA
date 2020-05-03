import com.google.gson.Gson;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class SingleThreadedServerClient extends Thread{
    private final static int NUM_BROTHERS = 2;
    private SocketChannel firstClientSocketChannel;
    private SocketChannel secondClientSocketChannel;

    private Selector clientSelector;
    private Selector serverSelector;

    private ServerSocketChannel serverSocketChannel;

    private int serverResponses;
    private int clientResponses;

    private LamportRequest lamportRequest;
    private int clock;
    private String process;
    private int id;

    public SingleThreadedServerClient(int clock, int port, int firstPort, int secondPort, int id, String process){
        this.clock = clock;
        this.process = process;
        this.id = id;
        lamportRequest = new LamportRequest(clock, process, id);
        serverResponses = 0;
        clientResponses = 0;

        try {
            createClient(firstPort, secondPort);
            System.out.println("Client created");
            createServer(port);
            System.out.println("Server created\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createClient(int firstPort, int secondPort) throws IOException {
        clientSelector = Selector.open();
        InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("localhost"), firstPort);
        firstClientSocketChannel = SocketChannel.open();
        firstClientSocketChannel.configureBlocking(false);
        firstClientSocketChannel.connect(addr);
        firstClientSocketChannel.register(clientSelector, SelectionKey.OP_CONNECT);
        System.out.println("Created first client with remote addr: " + firstClientSocketChannel.getRemoteAddress());

        addr = new InetSocketAddress(InetAddress.getByName("localhost"), secondPort);
        secondClientSocketChannel = SocketChannel.open();
        secondClientSocketChannel.configureBlocking(false);
        secondClientSocketChannel.connect(addr);
        secondClientSocketChannel.register(clientSelector, SelectionKey.OP_CONNECT);
        System.out.println("Created second client with remote addr: " + secondClientSocketChannel.getRemoteAddress());
        //System.out.println("Opening sockets to port " + firstPort + " and port " + secondPort);
    }

    private void createServer(int port) throws IOException {
        InetAddress host = InetAddress.getByName("localhost");
        serverSelector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(host, port));
        serverSocketChannel.register(serverSelector, SelectionKey.OP_ACCEPT);
    }


    @Override
    public void run() {
        while (true) {
            try {
                if (serverResponses < NUM_BROTHERS){
                    System.out.println("\n~~ ATTENDING SERVER ~~");
                    attendServer();
                }
                if (clientResponses < NUM_BROTHERS){
                    System.out.println("\n~~ ATTENDING CLIENT ~~");
                    attendClient();
                }
                if (serverResponses >= NUM_BROTHERS && clientResponses >= NUM_BROTHERS){
                    System.out.println("\n\tI'M A FUCKING BEAST AND NOW MUST USE SCREEN");
                    System.exit(0);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void attendServer() throws IOException {
        // Wait for an event one of the server's registered channels
        serverSelector.select();

        // Iterate over the set of keys for which events are available
        Iterator iterator = serverSelector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            SelectionKey key = (SelectionKey) iterator.next();
            iterator.remove();
            ByteBuffer bb = ByteBuffer.allocate(1024);

            if (key.isAcceptable()) {
                SocketChannel sc = serverSocketChannel.accept();
                sc.configureBlocking(false);
                sc.register(serverSelector, SelectionKey.OP_READ);
                System.out.println("[SERVER] Remote connection Accepted: " + sc.getRemoteAddress());
            }

            if (key.isReadable()) {
                SocketChannel sc = (SocketChannel) key.channel();
                sc.read(bb);
                String result = new String(bb.array()).trim();
                System.out.println("[SERVER] Message received: " + result + " from local " + sc.getLocalAddress() + " and remote " + sc.getRemoteAddress());
                if (result.length() <= 0) {
                    sc.close();
                    System.out.println("[SERVER] Connection closed...");
                    System.out.println("[SERVER] Server will keep running. Try running another client to re-establish connection");
                }else {
                    result = lamportRequest.toString().replace("LamportRequest", "ResponseRequest");
                    System.out.println("[SERVER] Answering with: " + result);
                    bb.clear();
                    bb.put (result.getBytes());
                    bb.flip();
                    sc.write (bb);
                    serverResponses++;
                }
            }

            if (key.isWritable()){
                System.out.println("Writable");
                SocketChannel sc = (SocketChannel) key.channel();
                sc.register(serverSelector, SelectionKey.OP_WRITE);
            }
        }
    }

    private void attendClient() throws IOException {
        int numChannels = clientSelector.select();
        if (numChannels > 0) {
            boolean doneStatus = false;

            SelectionKey key = null;
            Iterator iterator = clientSelector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                key = (SelectionKey) iterator.next();
                //iterator.remove();

                if (key.isConnectable()) {
                    Boolean connected = processConnect(key);
                    System.out.println("[CLIENT] Processed connect from " + ((SocketChannel)key.channel()).getLocalAddress());
                    key.attach(((SocketChannel)key.channel()).getLocalAddress());
                    if (!connected) {
                        doneStatus = true;
                        break;
                    }
                    key.interestOps(SelectionKey.OP_WRITE);
                }
                if (key.isReadable()) {
                    ByteBuffer bb = ByteBuffer.allocate(1024);
                    SocketChannel sc = (SocketChannel) key.channel();
                    sc.read(bb);
                    String msg = new String(bb.array()).trim();
                    System.out.println("[CLIENT] Message received from Server: " + msg + " from remote: " + sc.getLocalAddress());
                    if (msg.equalsIgnoreCase("quit")) {
                        doneStatus = true;
                        break;

                    }else {
                        System.out.println("Got a response request: " + msg);
                        clientResponses++;
                    }
                    /*else if (msg.contains("LamportRequest")){
                        msg = msg.replace("LamportRequest", "");
                        Gson gson = new Gson();
                        LamportRequest lamportRequest = gson.fromJson(msg, LamportRequest.class);
                        System.out.println("[CLIENT] Parsed this request: " + lamportRequest);
                    }else if (msg.contains("ResponseRequest")){
                        System.out.println("Got a response request: " + msg);
                    }

                         */
                    iterator.remove();
                }
                if (key.isWritable()) {
                    lamportRequest = new LamportRequest(clock, process, id);

                    //System.out.print("[CLIENT] Type a message (type quit to stop): ");
                    //String msg = input.readLine();

                    SocketChannel sc = (SocketChannel) key.channel();
                    ByteBuffer bb = ByteBuffer.wrap(lamportRequest.toString().getBytes());
                    System.out.println("[CLIENT] Writing: " + lamportRequest.toString() + " to " + sc.getRemoteAddress());
                    sc.write(bb);
                    iterator.remove();
                    key.interestOps(SelectionKey.OP_READ);
                }
            }

            if (doneStatus) {
                System.out.println("[CLIENT] Done status");
                //break;
            }
        }
    }

    public static Boolean processConnect(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        try {
            while (sc.isConnectionPending()) {
                sc.finishConnect();
            }
        } catch (IOException e) {
            key.cancel();
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
