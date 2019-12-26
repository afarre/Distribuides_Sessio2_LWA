import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class DedicatedOutgoingSocket extends Thread {
    private int FIRST_OUTGOING_PORT;
    private int SECOND_OUTGOING_PORT;

    private Socket firstSocket;
    private DataInputStream firstDataInputStream;
    private DataOutputStream firstDataOutputStream;

    private Socket secondSocket;
    private DataInputStream secondDataInputStream;
    private DataOutputStream secondDataOutputStream;

    private AnalogueCommsLWA analogueCommsLWA;
    private String TMSTP;
    private int id;
    private S_LWA parent;

    public DedicatedOutgoingSocket(S_LWA s_lwa, int first_outgoing_port, int second_outgoing_port, String tmstp, AnalogueCommsLWA analogueCommsLWA, int id) {
        FIRST_OUTGOING_PORT = first_outgoing_port;
        SECOND_OUTGOING_PORT = second_outgoing_port;
        TMSTP = tmstp;
        this.analogueCommsLWA = analogueCommsLWA;
        this.id = id;
        parent = s_lwa;
    }

    @Override
    public void run() {
        try {
            InetAddress iAddress = InetAddress.getLocalHost();
            String IP = iAddress.getHostAddress();

            firstSocket = new Socket(String.valueOf(IP), FIRST_OUTGOING_PORT);
            firstDataOutputStream = new DataOutputStream(firstSocket.getOutputStream());
            firstDataInputStream = new DataInputStream(firstSocket.getInputStream());

            secondSocket = new Socket(String.valueOf(IP), SECOND_OUTGOING_PORT);
            secondDataOutputStream = new DataOutputStream(secondSocket.getOutputStream());
            secondDataInputStream = new DataInputStream(secondSocket.getInputStream());

            while (true){
                firstDataOutputStream.writeUTF(TMSTP);
                secondDataOutputStream.writeUTF(TMSTP);
                System.out.println("\t[SENDING] timestamp: " + TMSTP);

                long time = new java.util.Date().getTime();
                firstDataOutputStream.writeLong(time);
                secondDataOutputStream.writeLong(time);
                System.out.println("\t[SENDING] time: " + time);

                firstDataOutputStream.writeInt(id);
                secondDataOutputStream.writeInt(id);
                System.out.println("\t[SENDING] id: " + id);

                analogueCommsLWA.addToQueue(time, TMSTP, id);
                LamportRequest merda = new LamportRequest(time, TMSTP, id);
                System.out.println("\tGenerated LamportRequest Sending end: " + merda.toString());

                long firstResponseTimestamp = firstDataInputStream.readLong();
                long secondResponseTimestamp = secondDataInputStream.readLong();

                System.out.println("\t[SENDER - RECEIVED] First timestamp: " + firstResponseTimestamp);
                System.out.println("\t[SENDER - RECEIVED] Second timestamp: " + secondResponseTimestamp);

                int firstId = firstDataInputStream.readInt();
                int secondId = secondDataInputStream.readInt();

                System.out.println("\t[SENDER - RECEIVED] First ID: " + firstId);
                System.out.println("\t[SENDER - RECEIVED] Second ID: " + secondId);

                LamportRequest queueRequest = analogueCommsLWA.queueContains(TMSTP);

                //System.out.println("\tQueue process: " + queueRequest.getProcess());
                //System.out.println("\tQueue ID: " + queueRequest.getId());
//                System.exit(0);

                if (queueRequest != null){
                    System.out.println("\tFirst IN");
                    System.out.println("\tLamport self time: " + time);
                    System.out.println("\tLamport first time: " + firstResponseTimestamp);
                    System.out.println("\tLamport second time: " + secondResponseTimestamp);
                    if (firstResponseTimestamp > time && secondResponseTimestamp > time){
                        System.out.println("Second IN");
                        for (int i = 0; i < 10; i++){
                            System.out.println("\tSoc el procés lightweight " + TMSTP);
                            Thread.sleep(1000);
                        }
                    }else if ((firstResponseTimestamp == time || secondResponseTimestamp == time) && (queueRequest.getId() < firstId || queueRequest.getId() < secondId)){
                        System.out.println("Third IN");
                        for (int i = 0; i < 10; i++){
                            System.out.println("\tSoc el procés lightweight " + TMSTP);
                            Thread.sleep(1000);
                        }
                    }else {
                        parent.waitForFreeCS();
                    }
                }
                //System.exit(0);

            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
