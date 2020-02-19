import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;

public class S_LWA extends Thread {
    private int OUTGOING_HWA_PORT;
    private int FIRST_OUTGOING_PORT;
    private int SECOND_OUTGOING_PORT;
    private String TMSTP;
    private DedicatedOutgoingSocket firstDedicatedOutgoing;
    private DedicatedOutgoingSocket secondDedicatedOutgoing;

    private Socket socketHWA;
    private DataInputStream diStreamHWA;
    private DataOutputStream doStreamHWA;

    private AnalogueCommsLWA analogueCommsLWA;
    private int id;
    private String className;

    public S_LWA(String className, int outgoingHwaPort, int myPort, int firstOutgoingPort, int secondOutgoingPort, String time_stamp_lwa, int id){
        this.OUTGOING_HWA_PORT = outgoingHwaPort;
        this.FIRST_OUTGOING_PORT = firstOutgoingPort;
        this.SECOND_OUTGOING_PORT = secondOutgoingPort;
        this.id = id;
        this.className = className;
        this.TMSTP = time_stamp_lwa;
        analogueCommsLWA = new AnalogueCommsLWA(this, myPort, time_stamp_lwa, id);
        analogueCommsLWA.start();
    }

    @Override
    public synchronized void run() {
        try {
            connectToParent();
            doStreamHWA.writeUTF("ONLINE");
            doStreamHWA.writeUTF(className);
            boolean connect = diStreamHWA.readBoolean();

            if (connect){
                firstDedicatedOutgoing = new DedicatedOutgoingSocket(this, FIRST_OUTGOING_PORT, TMSTP, analogueCommsLWA, id);
                firstDedicatedOutgoing.start();
                secondDedicatedOutgoing = new DedicatedOutgoingSocket(this, SECOND_OUTGOING_PORT, TMSTP, analogueCommsLWA, id);
                secondDedicatedOutgoing.start();
                analogueCommsLWA.registerDedicateds(firstDedicatedOutgoing, secondDedicatedOutgoing);
            }

            while (true){
                analogueCommsLWA.makeRequest();
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (ConnectException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void useScreen() {
        for (int i = 0; i <= 10; i++){
            System.out.println("\tSoc el procés lightweight " + TMSTP);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private void connectToParent() throws IOException {
        InetAddress iAddress = InetAddress.getLocalHost();
        String IP = iAddress.getHostAddress();

        System.out.println(className + " connecting to parent");
        socketHWA = new Socket(String.valueOf(IP), OUTGOING_HWA_PORT);
        doStreamHWA = new DataOutputStream(socketHWA.getOutputStream());
        diStreamHWA = new DataInputStream(socketHWA.getInputStream());
    }


    public void notifyDone() throws IOException {
        doStreamHWA.writeUTF("LWA1_DONE");
    }

    public void waitForFreeCS() {
        analogueCommsLWA.waitForFreeCS();
    }
}
