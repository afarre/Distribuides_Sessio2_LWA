package socket;

import com.google.gson.Gson;
import model.LamportRequest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class SingleNonBlocking extends Thread{
    /** Classes necesaries per als NIO sockets **/
    private final Selector selector;
    private final ServerSocketChannel serverSocketChannel;
    private final SocketChannel socketChannel1;
    private final SocketChannel socketChannel2;

    /** Variables per al control de la comunicacio **/
    private String firstResponder;
    private String secondResponder;
    private boolean firstResponse;
    private boolean secondResponse;

    /** Constants per al algoritme de lamport **/
    private final static String LAMPORT_REQUEST = "LamportRequest";
    private final static String RESPONSE_REQUEST = "ResponseRequest";
    private final static String REMOVE_REQUEST = "RemoveRequest";

    /** Variables relacionades amb Lamport i la comunicacio entre classes **/
    private LamportRequest lamportRequest;
    private int clock;
    private final String process;
    private final int id;
    private final S_LWA s_lwa;

    public SingleNonBlocking(S_LWA s_lwa, int clock, int myPort, int firstPort, int secondPort, int id, String process) throws IOException {
        this.s_lwa = s_lwa;
        this.clock = clock;
        this.process = process;
        this.id = id;
        lamportRequest = new LamportRequest(clock, process, id);

        // Creo el servidor
        InetAddress host = InetAddress.getByName("localhost");
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(host, myPort));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Creo el primer client
        InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName("localhost"), firstPort);
        socketChannel1 = SocketChannel.open();
        socketChannel1.configureBlocking(false);
        socketChannel1.connect(addr);
        socketChannel1.register(selector, SelectionKey.OP_CONNECT |SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        // Creo el segon client
        addr = new InetSocketAddress(InetAddress.getByName("localhost"), secondPort);
        socketChannel2 = SocketChannel.open();
        socketChannel2.configureBlocking(false);
        socketChannel2.connect(addr);
        socketChannel2.register(selector, SelectionKey.OP_CONNECT |SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        firstResponse = false;
        secondResponse = false;
    }


    @Override
    public void run() {
        try {
            while (true){
                // Esperem a que s'activi algun flag d'alguna key del selector
                selector.select(500);

                // Iterem sobre aquelles keys que tenen algun flag activat
                Iterator iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()){
                    SelectionKey key = (SelectionKey)iterator.next();
                    // Eliminem la key del Set per evitar queryejar-la quan ja no tingui cap flag activat
                    iterator.remove();

                    // Acceptable: Flag atribuit nomes al servidor (ServerSocketChannel) per a conexions entrants.
                    if (key.isAcceptable()){
                        SocketChannel sc = serverSocketChannel.accept();
                        sc.configureBlocking(false);
                        sc.register(selector, SelectionKey.OP_READ);
                        key.attach(sc.getRemoteAddress());
                    }

                    // Connectable: Key atribuida a clients, on es valida la conexio.
                    if (key.isConnectable()){
                        Boolean connected = processConnect(key);
                        key.attach("CLIENT: " + ((SocketChannel)key.channel()).getLocalAddress());
                        if (!connected) {
                            return;
                        }
                    }

                    // Readable: La key te algun missatge per a ser llegit
                    if (key.isReadable()){
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer bb = ByteBuffer.allocate(1024);
                        bb.clear();
                        sc.read(bb);
                        String result = new String(bb.array()).trim();

                        // En base al missatge llegit, ens comportem/contestem d'una forma o una altra
                        if (result.contains(RESPONSE_REQUEST)) {
                            Gson gson = new Gson();
                            LamportRequest lamportRequest = gson.fromJson(result.replace(RESPONSE_REQUEST, ""), LamportRequest.class);
                            assignResponder(lamportRequest.getProcess(), RESPONSE_REQUEST);
                            if (firstResponse && secondResponse){
                                done();
                            }

                        }else if(result.contains(REMOVE_REQUEST) && result.contains(LAMPORT_REQUEST)) {
                            String[] aux = result.split(LAMPORT_REQUEST);
                            Gson gson = new Gson();
                            LamportRequest lamportRequest = gson.fromJson(aux[0].replace(REMOVE_REQUEST, ""), LamportRequest.class);
                            s_lwa.removeRequest(lamportRequest);
                            assignResponder(lamportRequest.getProcess(), REMOVE_REQUEST);

                            if (firstResponse && secondResponse){
                                done();
                            }

                            gson = new Gson();
                            lamportRequest = gson.fromJson(aux[1], LamportRequest.class);
                            s_lwa.addRequest(lamportRequest);
                            result = this.lamportRequest.toString().replace(LAMPORT_REQUEST, RESPONSE_REQUEST);
                            bb.clear();
                            bb.put (result.getBytes());
                            bb.flip();
                            sc.write(bb);

                        }else if (result.contains(LAMPORT_REQUEST)) {
                            Gson gson = new Gson();
                            LamportRequest lamportRequest = gson.fromJson(result.replace(LAMPORT_REQUEST, ""), LamportRequest.class);
                            assignResponder(lamportRequest.getProcess(), LAMPORT_REQUEST);
                            s_lwa.addRequest(lamportRequest);
                            result = this.lamportRequest.toString().replace(LAMPORT_REQUEST, RESPONSE_REQUEST);
                            bb.clear();
                            bb.put (result.getBytes());
                            bb.flip();
                            sc.write(bb);

                        }else if (result.contains(REMOVE_REQUEST)){
                            Gson gson = new Gson();
                            LamportRequest lamportRequest = gson.fromJson(result.replace(REMOVE_REQUEST, ""), LamportRequest.class);
                            s_lwa.removeRequest(lamportRequest);
                            assignResponder(lamportRequest.getProcess(), REMOVE_REQUEST);

                            if (firstResponse && secondResponse){
                                done();
                            }
                        }
                    }

                    // Writable: La key te el flag d'escriptura activat
                    if (key.isWritable()){
                        String msg = lamportRequest.toString();
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer bb = ByteBuffer.wrap(msg.getBytes());
                        sc.write(bb);
                        s_lwa.addRequest(lamportRequest);
                        sc.register(selector, SelectionKey.OP_READ);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void assignResponder(String process, String ops) {
        if (firstResponder == null){
            firstResponder = process;
        }else if (secondResponder == null && !process.equals(firstResponder)){
            secondResponder = process;
        }

        if (ops.equals(RESPONSE_REQUEST)){
            if (process.equals(firstResponder)){
                firstResponse = true;
            }else if (process.equals(secondResponder)){
                secondResponse = true;
            }
        }
    }


    private void done() throws IOException {
        if (s_lwa.checkQueue()){
            s_lwa.useScreen();
            sendRemove();
            s_lwa.communicateDone(process);
        }
    }

    private void sendRemove() throws IOException {
        //Send remove
        String msg = lamportRequest.toString().replace(LAMPORT_REQUEST, REMOVE_REQUEST);
        ByteBuffer bb = ByteBuffer.wrap(msg.getBytes());
        socketChannel1.write(bb);

        bb = ByteBuffer.wrap(msg.getBytes());
        socketChannel2.write(bb);
        s_lwa.removeRequest(lamportRequest);

        //Send newly updated model.LamportRequest
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
