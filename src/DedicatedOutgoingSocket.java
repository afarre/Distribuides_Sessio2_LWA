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
    private String TMSTP;
    private long requestTime;
    private int id;
    private S_LWA parent;
    private boolean requestAttended;

    public DedicatedOutgoingSocket(S_LWA s_lwa, int outgoing_port, String tmstp, AnalogueCommsLWA analogueCommsLWA, int id) {
        OUTGOING_PORT = outgoing_port;
        TMSTP = tmstp;
        this.analogueCommsLWA = analogueCommsLWA;
        this.id = id;
        parent = s_lwa;

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

    }

    public void releaseCS(String tmstp) throws IOException {
        doStream.writeUTF("RELEASE");
        doStream.writeUTF(tmstp);
    }


    public void sendRequest(long time) throws IOException {
        this.requestTime = time;
        doStream.writeUTF(TMSTP);
        //System.out.println("\t[SENDING] Timestamp: " + TMSTP);

        doStream.writeLong(time);
        System.out.println("\t[SENDING] Time: " + time);

        doStream.writeInt(id);
        //System.out.println("\t[SENDING] ID: " + id);

        analogueCommsLWA.addToQueue(time, TMSTP, id);
        //System.out.println("\t rip sending end?");

        try {
            waitRequestResponse();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void waitRequestResponse() throws IOException, InterruptedException {
        //wait for request response
        long responseTime = diStream.readLong();

        int firstId = diStream.readInt();
        System.out.println("\t[SENDER - RECEIVED] Timestamp[" + responseTime + "] and ID[" + firstId + "]");
        //System.out.println("\t[SENDER - RECEIVED] ID: " + firstId);

        if (!analogueCommsLWA.isGotAnswer()){
            //first answer. change flag
            analogueCommsLWA.setGotAnswer(true);
        }else {
            //second answer. Must check queue
            //TODO: must check queue constantment fins que s'han borrat les altres requests i em toque a mi
            analogueCommsLWA.setRequestData(TMSTP, requestTime);
            analogueCommsLWA.checkCSAvailability();
            //reset answer flag
            analogueCommsLWA.setGotAnswer(false);
        }
    }

}
