import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class NIOClient {
    private static SocketChannel firstClient;
    private static SocketChannel secondClient;
    private static ByteBuffer firstBuffer;
    private static ByteBuffer secondBuffer;

    private String process;
    private int clock;
    private int id;

    public NIOClient(int clock, int firstPort, int secondPort, int id, String process) {
        this.process = process;
        this.clock = clock;
        this.id = id;

        try {
            System.out.println("\tOpening sockets to port " + firstPort + " and port " + secondPort);
            firstClient = SocketChannel.open(new InetSocketAddress("localhost", firstPort));
            secondClient = SocketChannel.open(new InetSocketAddress("localhost", secondPort));
            firstBuffer = ByteBuffer.allocate(1024);
            secondBuffer = ByteBuffer.allocate(1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendRequests() {
        LamportRequest lamportRequest = new LamportRequest(clock, process, id);
        firstBuffer = ByteBuffer.wrap(lamportRequest.toString().getBytes());
        secondBuffer = ByteBuffer.wrap(lamportRequest.toString().getBytes());
        String converted = new String(firstBuffer.array(), StandardCharsets.UTF_8);
        System.out.println("\tSending lamport request: " + converted);
        try {
            firstClient.write(firstBuffer);
            secondClient.write(secondBuffer);
            firstBuffer.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void stop() throws IOException {
        firstClient.close();
        firstBuffer = null;
    }
}