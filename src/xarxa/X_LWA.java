package xarxa;

import model.LamportRequest;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class X_LWA extends Thread {
    private static int INCOMING_PORT;
    private static int OUTGOING_PORT;
    private final static String TOKEN_B = "TOKEN_B";
    private final static String TOKEN_A = "TOKEN_A";


    /** Variables per al control de la comunicacio **/
    private String firstResponder;
    private String secondResponder;
    private boolean firstResponse;
    private boolean secondResponse;

    /** Constants per al algoritme de lamport i altres comunicacions **/
    private final static String RESPONSE_REQUEST = "ResponseRequest";

    /** Dades de la peticio de lamport actual **/
    private final String process;
    private final int id;

    private final ArrayList<LamportRequest> lamportQueue;
    private LamportRequest lamportRequest;
    private OutgoingSocket outgoingSocket;
    private int clock;

    public X_LWA(String process, int myPort, int id, int xarxaPort){
        this.process = process;
        INCOMING_PORT = myPort;
        OUTGOING_PORT = xarxaPort;
        this.id = id;
        clock = 0;
        lamportRequest = new LamportRequest(clock, process, id);
        lamportQueue = new ArrayList<>();
    }

    @Override
    public void run() {
        outgoingSocket = new OutgoingSocket(OUTGOING_PORT, this, process, INCOMING_PORT);
        outgoingSocket.start();
        createIncomeConnection();
    }

    private void createIncomeConnection() {
        try {
            //creem el nostre socket
            ServerSocket serverSocket = new ServerSocket(INCOMING_PORT);
            while (true){
                //esperem a la conexio de la xarxa de comunicacions
                Socket incomeSocket = serverSocket.accept();
                IncomingSocket incomingSocket = new IncomingSocket(incomeSocket, this, process);
                incomingSocket.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void assignResponder(String process, String ops) {
        if (firstResponder == null){
            firstResponder = process;
        }else if (secondResponder == null && !process.equals(firstResponder)){
            secondResponder = process;
        }

        if (ops.equals(RESPONSE_REQUEST)){
            if (process.equals(firstResponder)){
                firstResponse = true;
            }else if (process.equals(secondResponder)){
                secondResponse = true;
            }
        }
    }

    public synchronized void useScreen() {
        //parentAllowance();
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

    public synchronized boolean checkQueue() {
        System.out.println("Query queue:");
        for (int i = 0; i < lamportQueue.size(); i++){
            System.out.println(lamportQueue.get(i).toString());
        }
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
        System.out.println("To be executed: " + toBeExecuted.toString());
        return toBeExecuted.getProcess().equals(process);
    }

    public synchronized void addRequest(LamportRequest lamportRequest) {
        if (!lamportQueue.contains(lamportRequest)){
            lamportQueue.add(lamportRequest);
        }
    }

    public synchronized void removeRequest(LamportRequest lamportRequest) {
        System.out.println("Removing: " + lamportRequest.toString());
        lamportQueue.remove(lamportRequest);
    }

    public LamportRequest getLamportRequest() {
        return lamportRequest;
    }

    public void sendRequest() {
        outgoingSocket.setAction("SEND");
        outgoingSocket.myNotify();
    }

    public void relayCheckQueue() throws IOException {
        outgoingSocket.setAction("CHECK");
        outgoingSocket.myNotify();
    }

    public void increaseLamportClock() {
        clock++;
        this.lamportRequest = new LamportRequest(clock, process, id);
    }
}
