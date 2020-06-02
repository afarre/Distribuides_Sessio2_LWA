package xarxa;

import com.google.gson.Gson;
import model.LamportRequest;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class IncomingSocket extends Thread{
    private DataInputStream diStream;
    private DataOutputStream doStream;
    private Socket socket;
    private X_LWA parent;

    private String process;
    private boolean checkQueueFlag;

    private final static String LWA1 = "LWA1";
    private final static String LWA2 = "LWA2";
    private final static String LWA3 = "LWA3";

    /** Constants per al algoritme de lamport **/
    private final static String LAMPORT_REQUEST = "LamportRequest";
    private final static String RESPONSE_REQUEST = "ResponseRequest";
    private final static String REMOVE_REQUEST = "RemoveRequest";
    private static final String WORK = "WORK";
    private final static String ONLINE = "ONLINE";
    private final static String PORT = "PORT";
    private final static String LWA_WORK = "LWA_WORK";

    public IncomingSocket(Socket socket, X_LWA parent, String process) {
        this.socket = socket;
        this.parent = parent;
        this.process = process;
        checkQueueFlag = false;
    }

    @Override
    public void run() {
        try {
            diStream = new DataInputStream(socket.getInputStream());
            doStream = new DataOutputStream(socket.getOutputStream());
            while (true){
                String request = diStream.readUTF();
                readRequest(request);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void readRequest(String request) throws IOException {
        Gson gson = new Gson();
        String response = null;
        LamportRequest lamportRequest = null;
        switch (request){
            case WORK:
                System.out.println("Got work in " + process + " in thread " + Thread.currentThread().getName());
                parent.sendRequest();
                /*
                doStream.writeUTF(parent.getLamportRequest().toString());
                parent.addRequest(parent.getLamportRequest());
                response = diStream.readUTF();
                System.out.println("First response = " + response);
                response = diStream.readUTF();
                System.out.println("Second response = " + response);
                done();

                 */
                break;

            case LAMPORT_REQUEST:
                request = diStream.readUTF();
                System.out.println("Lamport request: " + request);
                lamportRequest = gson.fromJson(request.replace(LAMPORT_REQUEST, ""), LamportRequest.class);
                parent.assignResponder(lamportRequest.getProcess(), LAMPORT_REQUEST);
                parent.addRequest(lamportRequest);
                response = parent.getLamportRequest().toString().replace(LAMPORT_REQUEST, RESPONSE_REQUEST);
                System.out.println("Answering with this response: " + response);
                doStream.writeUTF(response);
                break;

            case RESPONSE_REQUEST:
                System.out.println("Got this response request: " + diStream.readUTF());
                /*
                if (!checkQueueFlag){
                    checkQueueFlag = true;
                }else {
                    checkQueueFlag = false;
                    if (parent.checkQueue()){
                        parent.useScreen();
                        sendRemove();
                        parent.communicateDone(process);
                    }
                }
                */
                break;
            case REMOVE_REQUEST:
                String msg = diStream.readUTF();
                lamportRequest = gson.fromJson(msg.replace(REMOVE_REQUEST, ""), LamportRequest.class);
                parent.removeRequest(lamportRequest);
                parent.relayCheckQueue();
                break;
        }
    }

    private void sendRemove() {

    }

    private void done() {
        /*
        if (parent.checkQueue()){
            parent.useScreen();
            sendRemove();
            parent.communicateDone(process);
        }

         */
    }
}
