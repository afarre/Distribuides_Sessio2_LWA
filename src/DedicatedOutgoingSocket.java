import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class DedicatedOutgoingSocket extends Thread {
    private int OUTGOING_PORT;
    private int SECOND_OUTGOING_PORT;

    private Socket socket;
    private DataInputStream diStream;
    private DataOutputStream doStream;


    private AnalogueCommsLWA analogueCommsLWA;
    private String TMSTP;
    private int id;
    private S_LWA parent;

    public DedicatedOutgoingSocket(S_LWA s_lwa, int outgoing_port, String tmstp, AnalogueCommsLWA analogueCommsLWA, int id) {
        OUTGOING_PORT = outgoing_port;
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

            socket = new Socket(String.valueOf(IP), OUTGOING_PORT);
            doStream = new DataOutputStream(socket.getOutputStream());
            diStream = new DataInputStream(socket.getInputStream());


            while (true){
                long requestTime = sendRequest();

                //wait for request response
                boolean requestAttended = false;
                boolean available = false;
                while (!requestAttended){
                    long responseTime = diStream.readLong();

                    System.out.println("\t[SENDER - RECEIVED] Timestamp: " + responseTime);
                    int firstId = diStream.readInt();
                    System.out.println("\t[SENDER - RECEIVED] ID: " + firstId);
                    LamportRequest queueRequest = analogueCommsLWA.queueContains(TMSTP);
                    if (!analogueCommsLWA.isGotAnswer()){
                        //first answer. change flag
                        analogueCommsLWA.setGotAnswer(true);
                    }else {
                        //second answer. Must check queue
                        available = analogueCommsLWA.checkCSAvailability(TMSTP, requestTime);
                        //reset answer flag
                        analogueCommsLWA.setGotAnswer(false);
                    }

                    if (available){
                        parent.useScreen();
                        analogueCommsLWA.releaseProcess(TMSTP);
                        System.out.println("### sending release msg ###");
                        //TODO: Estic enviant un sol release desde un dels dos DedicatedOutgoing, quan s'hauria d'enviar en els dos a traves de anaoguecomms
                        doStream.writeUTF("RELEASE");
                        doStream.writeUTF(TMSTP);
                        requestAttended = true;
                    }

/*
                    if (queueRequest != null && analogueCommsLWA.lesserTimestamp(requestTime)){
                        System.out.println("\tFirst IN");
                        System.out.println("\tLamport self time: " + requestTime);
                        System.out.println("\tLamport first time: " + responseTime);
                        if (responseTime > requestTime){
                            System.out.println("Second IN");
                            parent.useScreen();
                            System.out.println("### sending release msg ###");
                            requestAttended = true;
                            // doStream.writeUTF("RELEASE");
                        }else if (responseTime == requestTime && queueRequest.getId() < firstId){
                            System.out.println("Third IN");
                            parent.useScreen();
                            System.out.println("### sending release msg ###");
                            requestAttended = true;
                            // doStream.writeUTF("RELEASE");
                        }else {
                            parent.waitForFreeCS();
                        }
                    }
                    */
                }

            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }


    private long sendRequest() throws IOException {
        doStream.writeUTF(TMSTP);
        System.out.println("\t[SENDING] Timestamp: " + TMSTP);

        long time = new java.util.Date().getTime();
        doStream.writeLong(time);
        System.out.println("\t[SENDING] Time: " + time);

        doStream.writeInt(id);
        System.out.println("\t[SENDING] ID: " + id);

        analogueCommsLWA.addToQueue(time, TMSTP, id);
        //System.out.println("\t rip sending end?");
        return time;
    }
}
