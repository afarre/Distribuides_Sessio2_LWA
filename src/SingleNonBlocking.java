import com.google.gson.Gson;
import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class SingleNonBlocking extends Thread{
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    private SocketChannel socketChannel1;
    private SocketChannel socketChannel2;

    private String firstResponder;
    private String secondResponder;
    private int firstResponderPort;
    private int secondResponderPort;
    private boolean firstResponse;
    private boolean secondResponse;

    private LamportRequest lamportRequest;
    private int clock;
    private String process;
    private int id;
    private S_LWA s_lwa;

    public SingleNonBlocking(S_LWA s_lwa, int clock, int myPort, int firstPort, int secondPort, int id, String process) throws IOException {
        this.s_lwa = s_lwa;
        this.clock = clock;
        this.process = process;
        this.id = id;
        lamportRequest = new LamportRequest(clock, process, id);

        InetAddress host = InetAddress.getByName("localhost");
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(host, myPort));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("localhost"), firstPort);
        socketChannel1 = SocketChannel.open();
        socketChannel1.configureBlocking(false);
        socketChannel1.connect(addr);
        socketChannel1.register(selector, SelectionKey.OP_CONNECT |SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        addr = new InetSocketAddress(InetAddress.getByName("localhost"), secondPort);
        socketChannel2 = SocketChannel.open();
        socketChannel2.configureBlocking(false);
        socketChannel2.connect(addr);
        socketChannel2.register(selector, SelectionKey.OP_CONNECT |SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        firstResponderPort = -1;
        secondResponderPort = -1;
        firstResponse = false;
        secondResponse = false;
    }


    @Override
    public void run() {
        try {
            while (true){
                int keysReady = selector.select(1000);
                System.out.println("There are " + keysReady + " in an I/O ready status.");

                Iterator iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()){
                    System.out.println("\n~~~~SELECTING NEXT KEY~~~~");
                    SelectionKey key = (SelectionKey)iterator.next();
                    iterator.remove();

                    if (key.isAcceptable()){
                        SocketChannel sc = serverSocketChannel.accept();
                        sc.configureBlocking(false);
                        sc.register(selector, SelectionKey.OP_READ);
                        System.out.println("[SERVER] Connection Accepted: " + sc.getRemoteAddress());
                        key.attach(sc.getRemoteAddress());

                        if (key.attachment() == null){
                            System.out.println("Key attachment: this key has no attachments");
                        }else {
                            System.out.println("Key attachment: " + key.attachment().toString());
                        }

                    }

                    if (key.isConnectable()){
                        Boolean connected = processConnect(key);
                        System.out.println("[CLIENT] Key connected: " + ((SocketChannel)key.channel()).getLocalAddress());
                        key.attach("CLIENT: " + ((SocketChannel)key.channel()).getLocalAddress());
                        if (!connected) {
                            return;
                        }

                        if (key.attachment() == null){
                            System.out.println("Key attachment: this key has no attachments");
                        }else {
                            System.out.println("Key attachment: " + key.attachment().toString());
                        }

                    }


                    if (key.isReadable()){
                        SocketChannel sc = (SocketChannel) key.channel();
                        System.out.println("Key operation: Read");

                        if (key.attachment() == null){
                            System.out.println("Key attachment: this key has no attachments, attaching it.");
                            key.attach("SERVER: " + sc.getRemoteAddress());
                            System.out.println("Key attachment: " + key.attachment().toString());
                        }else {
                            System.out.println("Key attachment: " + key.attachment().toString());
                        }

                        ByteBuffer bb = ByteBuffer.allocate(1024);
                        bb.clear();
                        sc.read(bb);
                        String result = new String(bb.array()).trim();
                        System.out.println("[SERVER] Message received: " + result + " Message length= " + result.length());

                        if (result.length() <= 0) {
                            sc.close();
                            System.out.println("Connection closed...");
                            System.out.println("Server will keep running. Try running another client to re-establish connection");
                        }else if (result.contains("ResponseRequest")) {
                            System.out.println("[SERVER] Got a response request");
                            Gson gson = new Gson();
                            LamportRequest lamportRequest = gson.fromJson(result.replace("ResponseRequest", ""), LamportRequest.class);
                            assignResponder(lamportRequest.getProcess(), "ResponseRequest");
                            if (firstResponse && secondResponse){
                                done();
                            }
                        }else if(result.contains("RemoveRequest") && result.contains("LamportRequest")) {
                            String aux[] = result.split("LamportRequest");
                            //remove
                            Gson gson = new Gson();
                            LamportRequest lamportRequest = gson.fromJson(aux[0].replace("RemoveRequest", ""), LamportRequest.class);
                            System.out.println("[SERVER] Got RemoveRequest: " + lamportRequest);
                            s_lwa.removeRequest(lamportRequest);
                            assignResponder(lamportRequest.getProcess(), "RemoveRequest");

                            if (firstResponse && secondResponse){
                                done();
                            }

                            //lamportRequest
                            gson = new Gson();
                            lamportRequest = gson.fromJson(aux[1], LamportRequest.class);
                            //assignResponder(lamportRequest.getProcess());
                            s_lwa.addRequest(lamportRequest);
                            result = this.lamportRequest.toString().replace("LamportRequest", "ResponseRequest");
                            System.out.println("[SERVER] Answering: " + result);
                            bb.clear();
                            bb.put (result.getBytes());
                            bb.flip();
                            sc.write (bb);
                        }else if (result.contains("LamportRequest")) {
                            Gson gson = new Gson();
                            LamportRequest lamportRequest = gson.fromJson(result.replace("LamportRequest", ""), LamportRequest.class);
                            assignResponder(lamportRequest.getProcess(), "LamportRequest");
                            s_lwa.addRequest(lamportRequest);
                            result = this.lamportRequest.toString().replace("LamportRequest", "ResponseRequest");
                            System.out.println("[SERVER] Answering: " + result);
                            bb.clear();

                            bb.put (result.getBytes());
                            bb.flip();

                            sc.write (bb);
                        }else if (result.contains("RemoveRequest")){
                            Gson gson = new Gson();
                            LamportRequest lamportRequest = gson.fromJson(result.replace("RemoveRequest", ""), LamportRequest.class);
                            System.out.println("[SERVER] Got RemoveRequest: " + lamportRequest);
                            s_lwa.removeRequest(lamportRequest);
                            assignResponder(lamportRequest.getProcess(), "RemoveRequest");

                            if (firstResponse && secondResponse){
                                done();
                            }
                        }
                    }

                    if (key.isWritable()){
                        System.out.println("Key operation: Write");
                        if (key.attachment() == null){
                            System.out.println("Key attachment: this key has no attachments");
                        }else {
                            System.out.println("Key attachment: " + key.attachment().toString());
                        }

                        //System.out.print("Type a message (type quit to stop): ");
                        //String msg = input.readLine();
                        String msg = lamportRequest.toString();
                        System.out.println("[CLIENT] Writing: " + msg);
                        if (msg.equalsIgnoreCase("quit")) {
                            return;
                        }
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer bb = ByteBuffer.wrap(msg.getBytes());
                        sc.write(bb);
                        s_lwa.addRequest(lamportRequest);
                        sc.register(selector, SelectionKey.OP_READ);
                    }
                }
                System.out.println("\n\n-_-_-_-SELECTING NEW KEY SET-_-_-_-");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void assignResponder(String process, String ops) {
        if (firstResponder == null){
            firstResponder = process;
            System.out.println("\tFirst responder = " + firstResponder);
        }else if (secondResponder == null && !process.equals(firstResponder)){
            secondResponder = process;
            System.out.println("\tSecond responder = " + secondResponder);
        }

        System.out.println("\tFirst responder = " + firstResponder);
        System.out.println("\tSecond responder = " + secondResponder);
        System.out.println("\tFirst response = " + firstResponse);
        System.out.println("\tSecond response = " + secondResponse);
        if (ops.equals("LamportRequest")){

        }else if (ops.equals("RemoveRequest")){
            /*
            if (process.equals(firstResponder)){
                firstResponse = false;
            }else if (process.equals(secondResponder)){
                secondResponse = false;
            }

             */
        }else if (ops.equals("ResponseRequest")){
            if (process.equals(firstResponder)){
                firstResponse = true;
            }else if (process.equals(secondResponder)){
                secondResponse = true;
            }
        }
        System.out.println("\tFirst response = " + firstResponse);
        System.out.println("\tSecond response = " + secondResponse);
    }


    private void done() throws IOException {
        System.out.println("\nGot both responses. Exiting.");
        //System.exit(0);
        if (s_lwa.checkQueue()){
            s_lwa.useScreen();
            sendRemove();
        }/*else {

            for (int i = 0; i <= 10; i++) {
                System.out.println("...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }*/
    }

    private void sendRemove() throws IOException {
        //Send remove
        String msg = lamportRequest.toString().replace("LamportRequest", "RemoveRequest");
        System.out.println("[CLIENT] Sending: " + msg);
        ByteBuffer bb = ByteBuffer.wrap(msg.getBytes());
        socketChannel1.write(bb);

        bb = ByteBuffer.wrap(msg.getBytes());
        socketChannel2.write(bb);
        s_lwa.removeRequest(lamportRequest);

        //Send newly updated LamportRequest
        clock++;
        lamportRequest = new LamportRequest(clock, process, id);

        bb = ByteBuffer.wrap(lamportRequest.toString().getBytes());
        socketChannel1.write(bb);

        bb = ByteBuffer.wrap(lamportRequest.toString().getBytes());
        socketChannel2.write(bb);
        s_lwa.addRequest(lamportRequest);
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
