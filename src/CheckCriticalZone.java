import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

public class CheckCriticalZone extends Thread {
    private AnalogueCommsLWA analogueCommsLWA;

    public CheckCriticalZone(AnalogueCommsLWA analogueCommsLWA){
        this.analogueCommsLWA = analogueCommsLWA;
    }

    @Override
    public void run() {
        while (true) {
            try {
                synchronized (this) {
                    this.wait();
                    String tmstp = analogueCommsLWA.getProcess();

                    if (checkQueue(tmstp)) {
                        analogueCommsLWA.useScreen();
                        try {
                            analogueCommsLWA.releaseProcess(tmstp);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        for (int i = 0; i <= 10; i++) {
                            System.out.println("...");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized boolean checkQueue(String process) {
        boolean available = true;
        int clock = analogueCommsLWA.getClock();
        int id = analogueCommsLWA.getTheId();
        CopyOnWriteArrayList<LamportRequest> lamportQueue = analogueCommsLWA.getLamportQueue();

        for (LamportRequest lr : lamportQueue) {
            System.out.println("[LAMPORT (query)]" + lr.toString());
        }

        System.out.println("Cheking access to CS. My process: " + process + "; My clock: " + clock + "; My id: " + id);
        for (LamportRequest lr : lamportQueue) {
            System.out.println("[LAMPORT (query conditionals)]" + lr.toString());
            if (!lr.getProcess().equals(process)) {
                if (lr.getClock() < clock) {
                    available = false;
                } else if (lr.getClock() == clock && lr.getId() < id) {
                    available = false;
                }
            }
        }

        return available;
    }

    public void myNotify() {
        synchronized (this){
            this.notify();
        }
    }
}
