package socket;

import model.LamportRequest;

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

    private final ArrayList<LamportRequest> lamportQueue;

    private final String process;
    private final int parentPort;
    private final int myPort;
    private final int firstPort;
    private final int secondPort;
    private final int id;

    public S_LWA(String process, int parentPort, int myPort, int firstPort, int secondPort, int id){
        this.process = process;
        this.parentPort = parentPort;
        this.myPort = myPort;
        this.firstPort = firstPort;
        this.secondPort = secondPort;
        this.id = id;
        lamportQueue = new ArrayList<>();
    }

    @Override
    public synchronized void run() {
        int clock = 0;
        try {
            connectToParent();
            doStreamHWA.writeUTF("ONLINE");
            doStreamHWA.writeUTF(process);
            diStreamHWA.readUTF();

            System.out.println("Setting up server with port: " + myPort);
            new SingleNonBlocking(this, clock, myPort, firstPort, secondPort, id, process).start();

        } catch (ConnectException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void useScreen() {
        parentAllowance();
        for (int i = 0; i < 10; i++){
            System.out.println("\tSoc el procés lightweight " + process);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("-- Iteració acabada --\n");
    }

    private void parentAllowance() {
        try {
            doStreamHWA.writeUTF("RUN STATUS");
            boolean childsDone = diStreamHWA.readBoolean();
            if (childsDone){
                diStreamHWA.readUTF();
            }
        } catch (IOException e) {
            e.printStackTrace();
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

    public void addRequest(LamportRequest lamportRequest) {
        if (!lamportQueue.contains(lamportRequest)){
            lamportQueue.add(lamportRequest);
        }
    }

    public boolean checkQueue() {
        LamportRequest toBeExecuted = null;
        for (int i = 0; i < lamportQueue.size(); i++){
            toBeExecuted = lamportQueue.get(i);
            for (int j = 1; j < lamportQueue.size(); j++){
                if (lamportQueue.get(j).getClock() < toBeExecuted.getClock()){
                    toBeExecuted = lamportQueue.get(j);
                }else if (lamportQueue.get(j).getClock() == toBeExecuted.getClock() && lamportQueue.get(j).getId() < toBeExecuted.getId()){
                    toBeExecuted = lamportQueue.get(j);
                }
            }
            if (toBeExecuted.equals(lamportQueue.get(i))){
                break;
            }
        }
        return toBeExecuted.getProcess().equals(process);
    }

    public void removeRequest(LamportRequest lamportRequest) {
        lamportQueue.remove(lamportRequest);
    }

    public void communicateDone(String process) throws IOException {
        doStreamHWA.writeUTF("LWA DONE");
        doStreamHWA.writeUTF(process);
    }
}
