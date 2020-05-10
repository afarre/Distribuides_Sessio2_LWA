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
    private TalkToBrotherSocket talkToBrotherSocket;
    private LamportRequest myRequest;

    private String process;
    private int parentPort;
    private int myPort;
    private int firstPort;
    private int secondPort;
    private int id;
    private int clock;

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
        clock = 0;
        try {
            connectToParent();
            doStreamHWA.writeUTF("ONLINE");
            doStreamHWA.writeUTF(process);
            boolean connect = diStreamHWA.readBoolean();

            if (connect){
                System.out.println("Setting up server with port: " + myPort);
                //SingleThreadedServerClient singleThreadedServerClient = new SingleThreadedServerClient(this, clock, myPort, firstPort, secondPort, id, process);
                //singleThreadedServerClient.start();
                SingleNonBlocking singleNonBlocking = new SingleNonBlocking(this, clock, myPort, firstPort, secondPort, id, process);
                singleNonBlocking.start();
                //talkToBrotherSocket = new TalkToBrotherSocket(this, clock, myPort, firstPort, secondPort, id, process);
                //talkToBrotherSocket.start();

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
            /*
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

             */
            System.out.println("\tmanage disconnections");
        }
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

    public boolean checkRequest(LamportRequest lamportRequest) {
        /*System.out.println("\n[DEBUG] Querying lamport queue");

        System.out.println("Checking request: " + lamportQueue.toString());
        for (int i = 0; i < lamportQueue.size(); i++){
            System.out.println("[DEBUG] " + lamportQueue.get(i).toString());
        }
        System.out.println("Contains: " + lamportQueue.contains(lamportRequest));


         */
        return !lamportQueue.contains(lamportRequest);
    }

    public void addRequest(LamportRequest lamportRequest) {
        if (!lamportQueue.contains(lamportRequest)){
            lamportQueue.add(lamportRequest);
        }
    }

    public boolean checkQueue() {
        boolean available = true;

        for (LamportRequest lr : lamportQueue) {
            System.out.println("[LAMPORT (query)]" + lr.toString());
        }

       // System.out.println("Cheking access to CS:\n\tMy process: " + process + ";\n\tMy clock: " + clock + ";\n\tMy id: " + id);
        for (LamportRequest lr : lamportQueue) {
         //   System.out.println("[LAMPORT (query conditionals for single request)]" + lr.toString());
            if (!lr.getProcess().equals(process)) {
           //     System.out.println("\tnom diferent");
                if (lr.getClock() < clock) {
             //       System.out.println("\thi ha un amb clock menor. available a false");
                    available = false;
                } else if (lr.getClock() == clock && lr.getId() < id) {
               //     System.out.println("\thi ha un amb id menor. available a false");
                    available = false;
                }
            }
        }
        //executedRequest = lamportRequest;
        return available;
    }

    public void setSentRequest(LamportRequest lamportRequest) {
        myRequest = lamportRequest;
    }

    public void removeRequest(LamportRequest lamportRequest) {
        lamportQueue.remove(lamportRequest);
        /*
        for (LamportRequest lr: lamportQueue){
            System.out.println("[DEBUG post remove]: " + lr.toString());
        }

         */
    }

    public void checkCS() {
        for (LamportRequest lr : lamportQueue){
            System.out.println(lr.toString());
        }
    }
}
