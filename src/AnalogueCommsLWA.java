import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class AnalogueCommsLWA extends Thread {
    private int MY_PORT;
    private DedicatedLWA dedicatedLWA;
    private final S_LWA s_lwa;
    private String time_stamp_lwa;
    private int id;
    private final ArrayList<Thread> dedicatedThreadList;

    public AnalogueCommsLWA(S_LWA s_lwa, int myPort, String time_stamp_lwa, int id) {
        this.MY_PORT = myPort;
        this.s_lwa = s_lwa;
        this.time_stamp_lwa = time_stamp_lwa;
        this.id = id;
        dedicatedThreadList = new ArrayList<>();
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
        dedicatedLWA = new DedicatedLWA(socket, this, time_stamp_lwa, id);
        Thread thread = new Thread(dedicatedLWA);
        dedicatedThreadList.add(thread);
        thread.start();
    }

    public void addToQueue(long time, String tmstp, int id) {
        do {
            //nop
        }while (dedicatedLWA == null);
        dedicatedLWA.addToQueue(time, tmstp, id);
        //TODO: Estic plenant la cua de nomes el ultim dedicateLWA
        /*
        for (int i = 0; i < dedicatedThreadList.size(); i++){
            dedicatedThreadList.get(i).addToQueue(time, tmstp, id);
        }

         */
    }

    public LamportRequest queueContains(String tmstp) {
        return dedicatedLWA.queueContains(tmstp);
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
}
