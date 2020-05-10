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
    private int firstPort;
    private int secondPort;
    private boolean firstWrite;
    private boolean secondWrite;
    private boolean removeRequest;
    private boolean executed;

    private LamportRequest lamportRequest;
    private int clock;
    private String process;
    private int id;
    private S_LWA s_lwa;


    public SingleThreadedServerClient(S_LWA s_lwa, int clock, int port, int firstPort, int secondPort, int id, String process){
        this.s_lwa = s_lwa;
        this.clock = clock;
        this.process = process;
        this.id = id;

        lamportRequest = new LamportRequest(clock, process, id);
        this.firstPort = -1;
        this.secondPort = -1;
        firstWrite = false;
        secondWrite = false;
        removeRequest = true;
        executed = false;

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
        //Server Socket Channels only accepts OP_ACCEPT -> .validOps()
        serverSocketChannel.register(serverSelector, SelectionKey.OP_ACCEPT);
    }


    @Override
    public void run() {
        while (true) {
            try {
                System.out.println("\n~~ ATTENDING SERVER ~~");
                attendServer();
                System.out.println("\n~~ ATTENDING CLIENT ~~");
                attendClient();

            //    if (serverResponses >= NUM_BROTHERS && clientResponses >= NUM_BROTHERS){
                if (serverResponses >= NUM_BROTHERS && firstWrite && secondWrite){
                    System.out.println("\n\tI'M A FUCKING BEAST AND NOW MUST USE SCREEN");
                    if (s_lwa.checkQueue()){
                        s_lwa.useScreen();
                        s_lwa.removeRequest(lamportRequest);
                        executed = true;
                        clock++;
                    }else {
                        for (int i = 0; i <= 10; i++) {
                            System.out.println("...");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void attendServer() throws IOException {
        // Wait for an event one of the server's registered channels
        serverSelector.select(1000);

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
                String msg = new String(bb.array()).trim();
                System.out.println("[SERVER] Message received: " + msg + " from local " + sc.getLocalAddress() + " and remote " + sc.getRemoteAddress());
                if (msg.length() <= 0) {
                    sc.close();
                    System.out.println("[SERVER] Connection closed...");
                    System.out.println("[SERVER] Server will keep running. Try running another client to re-establish connection");
                }else if (msg.contains("LamportRequest")){
                    //String aux = msg.replace("LamportRequest", "");
                    String aux [] = msg.split("LamportRequest");
                    for (int i = 1; i < aux.length; i++){
                        Gson gson = new Gson();
                        LamportRequest lamportRequest = gson.fromJson(aux[i], LamportRequest.class);
                        s_lwa.addRequest(lamportRequest);

                        msg = this.lamportRequest.toString().replace("LamportRequest", "ResponseRequest");
                        System.out.println("[SERVER] Answering with: " + msg);
                        bb.clear();
                        bb.put(msg.getBytes());
                        bb.flip();
                        sc.write(bb);
                    }
                }else if (msg.contains("RemoveRequest")){
                    System.out.println("[SERVER] Got a remove request with the following msg: " + msg);
                }
            }

            /*
            // Unused
            if (key.isWritable()){
                System.out.println("Writable");
                SocketChannel sc = (SocketChannel) key.channel();
                sc.register(serverSelector, SelectionKey.OP_WRITE);
            }

             */
        }
    }

    private void attendClient() throws IOException {
        int numChannels = clientSelector.select(1000);
        System.out.println("Num channels: " + numChannels);
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
                    if (firstPort == -1){
                        firstPort = ((SocketChannel) key.channel()).socket().getPort();
                    }else if (secondPort == -1){
                        secondPort = ((SocketChannel) key.channel()).socket().getPort();
                    }
                }

                if (key.isReadable()) {
                    ByteBuffer bb = ByteBuffer.allocate(1024);
                    SocketChannel sc = (SocketChannel) key.channel();
                    sc.read(bb);
                    String msg = new String(bb.array()).trim();
                    System.out.println("[CLIENT] Message received from Server: \"" + msg + "\" from remote: " + sc.getLocalAddress() + " from key with attachment: " + key.attachment().toString());
                    if (msg.equalsIgnoreCase("quit")) {
                        doneStatus = true;
                        break;

                    }else {
                        System.out.println("Got a response request: " + msg);
                        //clientResponses++;
                        if (((SocketChannel) key.channel()).socket().getPort() == firstPort){
                            firstWrite = true;
                        }else if (((SocketChannel) key.channel()).socket().getPort() == secondPort){
                            secondWrite = true;
                        }
                        key.interestOps(SelectionKey.OP_WRITE);
                    }
                }

                if (key.isWritable()) {
                    SocketChannel sc = (SocketChannel) key.channel();
                    ByteBuffer bb = null;

                    if (executed){
                        String msg = this.lamportRequest.toString().replace("LamportRequest", "RemoveRequest");
                        System.out.println("[CLIENT] Writing: " + msg + " to " + sc.getRemoteAddress() + " from key with attachment: " + key.attachment().toString());
                        bb = ByteBuffer.wrap(lamportRequest.toString().getBytes());
                        sc.write(bb);
                    }

                    lamportRequest = new LamportRequest(clock, process, id);
                    bb = ByteBuffer.wrap(lamportRequest.toString().getBytes());
                    System.out.println("[CLIENT] Writing: " + lamportRequest.toString() + " to " + sc.getRemoteAddress() + " from key with attachment: " + key.attachment().toString());
                    s_lwa.addRequest(lamportRequest);
                    sc.write(bb);
                    key.interestOps(SelectionKey.OP_READ);
                }
            }

            if (doneStatus) {
                System.out.println("[CLIENT] Done status");
                //break;
            }
        }
    }

    private void sendRemove() throws IOException {
        int counter = 0;
        Iterator iterator = clientSelector.selectedKeys().iterator();
        for (Iterator it = iterator; it.hasNext(); ) {
            counter++;
        }
        System.out.println("Must remove items from this many clients: " + counter);
        /*
        ByteBuffer bb1 = ByteBuffer.allocate(1024);
        String msg = lamportRequest.toString().replace("LamportRequest", "RemoveRequest");
        bb1.put(msg.getBytes());
        firstClientSocketChannel.write(bb1);
        System.out.println("[CLIENT] Wrote: " + msg + " to first client.");

        // Contents of ByteBuffer are erased after a write
        ByteBuffer bb2 = ByteBuffer.allocate(1024);
        msg = lamportRequest.toString().replace("LamportRequest", "RemoveRequest");
        bb2.put(msg.getBytes());
        firstClientSocketChannel.write(bb2);
        System.out.println("[CLIENT] Wrote: " + msg + " to second client.");

         */
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
