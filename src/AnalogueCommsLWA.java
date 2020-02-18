import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;

public class AnalogueCommsLWA extends Thread {
    private int MY_PORT;
    private final S_LWA s_lwa;
    private String time_stamp_lwa;
    private int id;
    private final ArrayList<Thread> dedicatedThreadList;
    private LinkedList<LamportRequest> lamportQueue;
    private boolean gotAnswer;

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

    public boolean checkCSAvailability(String TMSTP, long requestTime){
        for (LamportRequest lr : lamportQueue){
            if (!lr.getProcess().equals(TMSTP)){
                if (lr.getTimeStamp() < requestTime){
                    return false;
                }
            }
        }
        return true;
    }

    public void addToQueue(long time, String process, int id) {
        LamportRequest request = new LamportRequest(time, process, id);
        boolean found = false;
        for (LamportRequest lr : lamportQueue){
            if (lr.getProcess().equals(process)){
                found = true;
                break;
            }
        }

        if (!found){
            System.out.println("Adding request to queue: " + request.toString());
            lamportQueue.add(request);
        }
    }

    public LamportRequest queueContains(String tmstp) {
        for (LamportRequest lamportRequest : lamportQueue) {
            if (lamportRequest.getProcess().equals(tmstp)) {
                return lamportRequest;
            }
        }
        return null;
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

    public boolean lesserTimestamp(long time) {
        for (LamportRequest lamportRequest : lamportQueue) {
            System.out.println("--- comparing original request time " + time + " with queue time " + lamportRequest.toString() + " ---");
            if (lamportRequest.getTimeStamp() < time) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean isGotAnswer() {
        return gotAnswer;
    }

    public synchronized void setGotAnswer(boolean gotAnswer) {
        this.gotAnswer = gotAnswer;
    }

    public void releaseProcess(String tmstp) {

    }
}
