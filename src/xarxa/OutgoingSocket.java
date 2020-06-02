package xarxa;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;

public class OutgoingSocket extends Thread{
    private DataInputStream diStream;
    private DataOutputStream doStream;
    private Socket outSocket;

    private final static String LAMPORT_REQUEST = "LamportRequest";
    private final static String REMOVE_REQUEST = "RemoveRequest";
    private final static String PORT = "PORT";
    private static final String TOKEN = "TOKEN";
    private static final String WORK = "WORK";

    private int OUTGOING_PORT;
    private int INCOMING_PORT;
    private String process;
    private X_LWA parent;

    public OutgoingSocket(int port, X_LWA x_lwa, String name, int incomingPort) {
        this.OUTGOING_PORT = port;
        this.INCOMING_PORT = incomingPort;
        parent = x_lwa;
        this.process = name;
    }

    @Override
    public void run() {
        System.out.println("starting connection to other with port " + OUTGOING_PORT);
        createOutcomeConnection();
        try {
            doStream.writeUTF(PORT);
            doStream.writeInt(INCOMING_PORT);
            doStream.writeUTF(process);
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (true){
            try {
                synchronized (this){
                    wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendRequest();
        }

    }

    private void sendRequest() {
        String response = null;
        try {
            doStream.writeUTF(LAMPORT_REQUEST);
            doStream.writeUTF(parent.getLamportRequest().toString());
            parent.addRequest(parent.getLamportRequest());
            response = diStream.readUTF();
            System.out.println("First response = " + response);
            response = diStream.readUTF();
            System.out.println("Second response = " + response);

            checkQueue();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void checkQueue() throws IOException {
        if (parent.checkQueue()){
            doStream.writeUTF("CS");
            diStream.readBoolean();
            parent.useScreen();
            sendRemove();
        }
    }

    private void sendRemove() {
        try {
            doStream.writeUTF("REMOVE");
            doStream.writeUTF(parent.getLamportRequest().toString().replace(LAMPORT_REQUEST, REMOVE_REQUEST));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createOutcomeConnection() {
        // Averiguem quina direccio IP hem d'utilitzar
        InetAddress iAddress;
        try {
            iAddress = InetAddress.getLocalHost();
            String IP = iAddress.getHostAddress();

            outSocket = new Socket(String.valueOf(IP), OUTGOING_PORT);
            doStream = new DataOutputStream(outSocket.getOutputStream());
            diStream = new DataInputStream(outSocket.getInputStream());
        } catch (ConnectException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void myNotify(){
        synchronized (this){
            this.notify();
        }
    }

}
