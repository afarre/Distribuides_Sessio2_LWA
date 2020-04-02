import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class DedicatedOutgoingSocket extends Thread {
    private int OUTGOING_PORT;

    private Socket socket;
    private DataInputStream diStream;
    private DataOutputStream doStream;

    private AnalogueCommsLWA analogueCommsLWA;
    private String process;
    private int clock;
    private int id;

    public DedicatedOutgoingSocket(S_LWA s_lwa, int outgoing_port, String tmstp, AnalogueCommsLWA analogueCommsLWA, int id) {
        OUTGOING_PORT = outgoing_port;
        process = tmstp;
        this.analogueCommsLWA = analogueCommsLWA;
        this.id = id;
        clock = analogueCommsLWA.getClock();

        try {
            InetAddress iAddress = InetAddress.getLocalHost();
            String IP = iAddress.getHostAddress();

            socket = new Socket(String.valueOf(IP), OUTGOING_PORT);
            doStream = new DataOutputStream(socket.getOutputStream());
            diStream = new DataInputStream(socket.getInputStream());

            //long requestTime = sendRequest();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true){
            try {
                sendRequest();
                synchronized (this){
                    this.wait();
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void releaseCS(String tmstp) throws IOException {
        doStream.writeUTF("RELEASE");
        doStream.writeUTF(tmstp);
    }


    public void sendRequest() throws IOException {
        doStream.writeUTF("LAMPORT REQUEST");
        doStream.writeUTF(process);
        //System.out.println("\t[SENDING] Timestamp: " + TMSTP);

        clock = analogueCommsLWA.getClock();
        doStream.writeInt(clock);
        //System.out.println("\t[SENDING] Time: " + time);

        doStream.writeInt(id);
        //System.out.println("\t[SENDING] ID: " + id);

        analogueCommsLWA.addToQueue(clock, process, id);
        //System.out.println("\t rip sending end?");

        if (OUTGOING_PORT == 55556){
            System.out.println("\tSENDING request (timestamp: " + clock + ") to TIME_STAMP_LWA2");
        }else  if (OUTGOING_PORT == 55557){
            System.out.println("\tSENDING request (timestamp: " + clock + ") to TIME_STAMP_LWA3");
        }else  if (OUTGOING_PORT == 55555){
            System.out.println("\tSENDING request (timestamp: " + clock + ") to TIME_STAMP_LWA1");
        }

        try {
            waitRequestResponse();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void waitRequestResponse() throws IOException, InterruptedException {
        //wait for request response
        int clock = diStream.readInt();

        int firstId = diStream.readInt();
        //System.out.println("\t[SENDER - RECEIVED] Timestamp[" + responseTime + "] and ID[" + firstId + "]");
        //System.out.println("\t[SENDER - RECEIVED] ID: " + firstId);

        analogueCommsLWA.checkBothAnswers(process, clock, OUTGOING_PORT);
    }

    public void myNotify() {
        synchronized (this){
            this.notify();
        }
    }

    public void myWait() {
        synchronized (this){
            try {
                System.out.println("prewait outgoing socket");
                this.wait();
                System.out.println("post wait outgoing socket");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}