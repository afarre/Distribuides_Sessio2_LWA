import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class NIOClient {
    private static SocketChannel firstSelector;
    private static SocketChannel secondSelector;
    private static ByteBuffer firstBuffer;
    private static ByteBuffer secondBuffer;
    private LamportRequest lamportRequest;

    private S_LWA s_lwa;
    private TalkToBrotherSocket talkToBrotherSocket;
    private int clock;
    private String process;
    private int id;

    public NIOClient(S_LWA s_lwa, TalkToBrotherSocket talkToBrotherSocket, int clock, int firstPort, int secondPort, int id, String process) {
        this.s_lwa = s_lwa;
        this.talkToBrotherSocket = talkToBrotherSocket;
        this.process = process;
        this.clock = clock;
        this.id = id;

        try {
            System.out.println("Opening sockets to port " + firstPort + " and port " + secondPort);
            firstSelector = SocketChannel.open(new InetSocketAddress("localhost", firstPort));
            secondSelector = SocketChannel.open(new InetSocketAddress("localhost", secondPort));

            firstBuffer = ByteBuffer.allocate(1024);
            secondBuffer = ByteBuffer.allocate(1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public LamportRequest sendRequest() {
        lamportRequest = new LamportRequest(clock, process, id);
        s_lwa.setSentRequest(lamportRequest);
        if (s_lwa.checkRequest(lamportRequest)){
            s_lwa.addRequest(lamportRequest);
            String msg = lamportRequest.toString();

            firstBuffer = ByteBuffer.wrap(msg.getBytes());
            secondBuffer = ByteBuffer.wrap(msg.getBytes());
            String converted = new String(firstBuffer.array(), StandardCharsets.UTF_8);
            System.out.println("\tSending my lamport request to both brothers: " + converted);
            try {
                firstSelector.write(firstBuffer);
                secondSelector.write(secondBuffer);
                firstBuffer.clear();
                secondBuffer.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return lamportRequest;
    }

    public void sendRequestReply(SelectionKey key){
        // Create a SocketChannel to read the request
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Socket socket = socketChannel.socket();
        SocketAddress remoteAddr = socket.getRemoteSocketAddress();
        System.out.println("\tWriting to " + remoteAddr);

        String msg = lamportRequest.toString();
        msg = msg.replace("LamportRequest",  "ResponseRequest");


        if (socket.getPort() == talkToBrotherSocket.getFirstRemoteSocketPort()){
            firstBuffer = ByteBuffer.wrap(msg.getBytes());
            try {
                firstSelector.write(firstBuffer);
                String converted = new String(firstBuffer.array(), StandardCharsets.UTF_8);
                System.out.println("Answering a request from key with port: " + socket.getPort() + ", with my lamportRequest: " + converted);
                firstBuffer.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else if (socket.getPort() == talkToBrotherSocket.getSecondRemoteSocketPort()){
            secondBuffer = ByteBuffer.wrap(msg.getBytes());
            try {
                secondSelector.write(secondBuffer);
                secondBuffer.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else {
            System.err.println("Current port does not match any of the client's ports. Exiting program.");
            System.exit(0);System.err.println("");
        }

        //System.exit(0);
    }

    public static void stop() throws IOException {
        firstSelector.close();
        firstBuffer = null;
    }

    public void releaseProcess(LamportRequest executedRequest) {
        clock++;
        String msg = executedRequest.toString().replace("LamportRequest", "Release");
        firstBuffer = ByteBuffer.wrap(msg.getBytes());
        secondBuffer = ByteBuffer.wrap(msg.getBytes());
        String converted = new String(firstBuffer.array(), StandardCharsets.UTF_8);
        System.out.println("Sending release request: " + converted);
        try {
            firstSelector.write(firstBuffer);
            secondSelector.write(secondBuffer);
            firstBuffer.clear();
            secondBuffer.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void read(int id) throws IOException {
        String msg = null;
        int readBytes = 0;
        if (id == 1){
            System.out.println("I must read first");
            firstBuffer.flip();
            firstBuffer.clear();

            readBytes = firstSelector.read(firstBuffer);

            msg = new String(firstBuffer.array()).trim();
            System.out.println("Read " + readBytes + " bytes from first client:" + msg);
        }else if (id == 2){
            System.out.println("I must read second");
            secondBuffer.flip();
            secondBuffer.clear();

            readBytes = secondSelector.read(secondBuffer);

            msg = new String(secondBuffer.array()).trim();
            System.out.println("Read " + readBytes + " bytes from second client:" + msg);
        }
    }

    public void identification() {
        String msg = "ID#1";
        firstBuffer = ByteBuffer.wrap(msg.getBytes());
        try {
            firstSelector.write(firstBuffer);
            firstBuffer.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }

        msg = "ID#2";
        secondBuffer = ByteBuffer.wrap(msg.getBytes());
        try {
            secondSelector.write(secondBuffer);
            secondBuffer.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}