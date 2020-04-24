import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

public class S_LWA extends Thread {
    private DataInputStream diStreamHWA;
    private DataOutputStream doStreamHWA;

    //private AnalogueCommsLWA analogueCommsLWA;
    private String lastExecuted;

    private ArrayList<LamportRequest> lamportQueue;

    private String process;
    private int parentPort;
    private int myPort;
    private int firstPort;
    private int secondPort;
    private int id;

    public S_LWA(String process, int parentPort, int myPort, int firstPort, int secondPort, int id){
        this.process = process;
        this.parentPort = parentPort;
        this.myPort = myPort;
        this.firstPort = firstPort;
        this.secondPort = secondPort;
        this.id = id;
        lamportQueue = new ArrayList<>();
        //analogueCommsLWA = new AnalogueCommsLWA(this, myPort, time_stamp_lwa, id);
        //analogueCommsLWA.start();
        lastExecuted = "";
    }

    @Override
    public synchronized void run() {
        int clock = 0;
        try {
            connectToParent();
            doStreamHWA.writeUTF("ONLINE");
            doStreamHWA.writeUTF(process);
            boolean connect = diStreamHWA.readBoolean();

            if (connect){
                System.out.println("Setting up server with port: " + myPort);
                TalkToBrotherSocket talkToBrotherSocket = new TalkToBrotherSocket(clock, myPort, firstPort, secondPort, id, process);
                talkToBrotherSocket.start();

                //NIOClient nioClient = new NIOClient(clock, firstPort, secondPort, id, process);
                //NIOClient.stop();
                //NIOClient.start(process, firstPort, secondPort);

         /*       try {
                    wait(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                boolean a = true;
                while (true){
                    //TODO: Check if CS is free in order to make a Lamport request.
                    if (a){
                        talkToBrotherSocket.makeRequest(process, id);
                        a = false;
                   }
                }
*/
                /*
                DedicatedOutgoingSocket firstDedicatedOutgoing = new DedicatedOutgoingSocket(this, FIRST_OUTGOING_PORT, TMSTP, analogueCommsLWA, id);
                firstDedicatedOutgoing.start();
                DedicatedOutgoingSocket secondDedicatedOutgoing = new DedicatedOutgoingSocket(this, SECOND_OUTGOING_PORT, TMSTP, analogueCommsLWA, id);
                secondDedicatedOutgoing.start();
                analogueCommsLWA.registerDedicateds(firstDedicatedOutgoing, secondDedicatedOutgoing);


                 */
                //analogueCommsLWA.makeRequest();
            }
        } catch (ConnectException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void useScreen() {
        lastExecuted = process;
        for (int i = 0; i < 10; i++){
            System.out.println("\tSoc el procés lightweight " + process);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (process.equals("LWA3")){
            try {
                doStreamHWA.writeUTF("LWA DONE");
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("waiting for test read");
            try {
                String aux = diStreamHWA.readUTF();
                System.out.println("I read: " + aux);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //analogueCommsLWA.stopLWA();
        }
        System.out.println("finished useScreen?");
    }


    private void connectToParent() throws IOException {
        InetAddress iAddress = InetAddress.getLocalHost();
        String IP = iAddress.getHostAddress();

        System.out.println(process + " connecting to parent");
        Socket socketHWA = new Socket(String.valueOf(IP), parentPort);
        doStreamHWA = new DataOutputStream(socketHWA.getOutputStream());
        diStreamHWA = new DataInputStream(socketHWA.getInputStream());
    }

    public String getLastExecuted() {
        return lastExecuted;
    }
}
