import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;

public class AnalogueCommsLWA extends Thread {
    private int MY_PORT;
    private final S_LWA s_lwa;
    private String time_stamp_lwa;
    private final ArrayList<Thread> dedicatedThreadList;
    private LinkedList<LamportRequest> lamportQueue;
    private boolean gotAnswer;

    private DedicatedOutgoingSocket firstDedicatedOutgoing;
    private DedicatedOutgoingSocket secondDedicatedOutgoing;

    private String tmstp;
    private long requestTime;
    private int id;

    public AnalogueCommsLWA(S_LWA s_lwa, int myPort, String time_stamp_lwa, int id) {
        this.MY_PORT = myPort;
        this.s_lwa = s_lwa;
        this.time_stamp_lwa = time_stamp_lwa;
        this.id = id;
        dedicatedThreadList = new ArrayList<>();
        lamportQueue = new LinkedList<>();
        gotAnswer = false;
    }

    @Override
    public void run() {
        try {
            //creem el nostre socket
            ServerSocket serverSocket = new ServerSocket(MY_PORT);
            while (true){
                Socket socket = serverSocket.accept();
                newDedicatedAnalogueComms(socket);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void newDedicatedAnalogueComms(Socket socket) {
        DedicatedLWA dedicatedLWA = new DedicatedLWA(socket, this, id);
        Thread thread = new Thread(dedicatedLWA);
        dedicatedThreadList.add(thread);
        thread.start();
    }

    public synchronized void checkCSAvailability(){
        boolean available = true;
        for (LamportRequest lr : lamportQueue){
            System.out.println("[LAMPORT (query)]" + lr.toString());
        }

        for (LamportRequest lr : lamportQueue){
            if (!lr.getProcess().equals(tmstp)){
                if (lr.getTimeStamp() < requestTime){
                    available = false;
                }else if (lr.getTimeStamp() == requestTime && lr.getId() > id){
                    available = false;
                }
            }
        }

        if (available){
            s_lwa.useScreen();
            try {
                releaseProcess(tmstp);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void addToQueue(long time, String process, int id) {
        LamportRequest request = new LamportRequest(time, process, id);
        boolean found = false;
        for (LamportRequest lr : lamportQueue){
            if (lr.getProcess().equals(process)){
                found = true;
                break;
            }
        }

        if (!found){
            for (LamportRequest lr : lamportQueue){
                System.out.println("[LAMPORT (add)]" + lr.toString());
            }
            lamportQueue.add(request);
        }
    }

    public void waitForFreeCS() {
        for (int i = 0; i < dedicatedThreadList.size(); i++){
            try {
                synchronized (dedicatedThreadList.get(i)){
                    dedicatedThreadList.get(i).wait();
                }
                //dedicatedThreadList.get(i).wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public synchronized void releaseProcess(String tmstp) throws IOException {
        System.out.println("### sending release msg to both dedicatedOutgoings ###");
        firstDedicatedOutgoing.releaseCS(tmstp);
        secondDedicatedOutgoing.releaseCS(tmstp);
        releaseRequest(tmstp);
        s_lwa.notify();
    }

    public void registerDedicateds(DedicatedOutgoingSocket firstDedicatedOutgoing, DedicatedOutgoingSocket secondDedicatedOutgoing) {
        this.firstDedicatedOutgoing = firstDedicatedOutgoing;
        this.secondDedicatedOutgoing = secondDedicatedOutgoing;
    }

    public void makeRequest() throws IOException {
        long time = new java.util.Date().getTime();
        firstDedicatedOutgoing.sendRequest(time);
        secondDedicatedOutgoing.sendRequest(time);
    }

    public synchronized boolean isGotAnswer() {
        return gotAnswer;
    }

    public synchronized void setGotAnswer(boolean gotAnswer) {
        this.gotAnswer = gotAnswer;
    }

    public synchronized void releaseRequest(String releaseProcess) {
        for (LamportRequest lr : lamportQueue){
            System.out.println("[LAMPORT (remove)]" + lr.toString());
        }
        lamportQueue.removeIf(lr -> lr.getProcess().equals(releaseProcess));
    }

    public void setRequestData(String tmstp, long requestTime) {
        this.tmstp = tmstp;
        this.requestTime = requestTime;
    }
}
