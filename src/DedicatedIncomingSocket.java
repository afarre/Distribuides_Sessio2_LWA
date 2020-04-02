import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CopyOnWriteArrayList;

public class DedicatedIncomingSocket implements Runnable {
    private static String process;

    private Socket socket;
    private DataInputStream diStream;
    private DataOutputStream doStream;

    private final AnalogueCommsLWA parent;
    private int id;

    public DedicatedIncomingSocket(Socket socket, AnalogueCommsLWA analogueCommsLWA, int id) {
        this.socket = socket;
        this.parent = analogueCommsLWA;
        this.id = id;
    }

    @Override
    public void run() {
        try {
            diStream = new DataInputStream(socket.getInputStream());
            doStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        while (true){
            try {
                System.out.println("\t\t\t\tWaiting for an incoming msg...");
                String request = diStream.readUTF();
                System.out.println("\t\t\t\tGot the request: " + request );
                actOnRequest(request);
            } catch (SocketException se){
                se.printStackTrace();
                System.exit(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void actOnRequest(String request) throws IOException {
        int clock;
        int id;
        switch (request){
            case "LAMPORT REQUEST":
                process = diStream.readUTF();
                clock = diStream.readInt();
                id = diStream.readInt();
                parent.addToQueue(clock, process, id);

                clock = getMyRequestedClock(process);
                doStream.writeInt(clock);
                doStream.writeInt(this.id);

                if (process.equals("TIME_STAMP_LWA1")){
                    System.out.println("\t\t\t\tAnswer to LWA1 done.");
                }else  if (process.equals("TIME_STAMP_LWA2")){
                    System.out.println("\t\t\t\tAnswer to LWA2 done.");
                }else {
                    System.out.println("\t\t\t\tAnswer to LWA3 done.");
                }

                parent.checkFullQueue();
                break;
            case "RELEASE":
                String releaseProcess = diStream.readUTF();
                System.out.println("\t\t\t\tReleasing process " + releaseProcess +  "...");
                parent.releaseRequest(releaseProcess);
                break;
        }
    }

    private synchronized int getMyRequestedClock(String process) {
        CopyOnWriteArrayList<LamportRequest> lamportRequest = parent.getLamportQueue();
        for (LamportRequest l : lamportRequest){
            if (l.getProcess().equals(process)){
                return l.getClock();
            }
        }
        return 0;
    }

    public void myNotify() {
        synchronized (this){
            this.notify();
        }
    }

    public void myWait() {
        synchronized (this){
            try {
                System.out.println("prewait incoming socket");
                this.wait();
                System.out.println("post wait incoming socket");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
