import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;

public class DedicatedLWA implements Runnable {
    private final static String TMSTP_LWA1 = "TIME_STAMP_LWA1";
    private final static String TMSTP_LWA2 = "TIME_STAMP_LWA2";
    private final static String TMSTP_LWA3 = "TIME_STAMP_LWA3";

    private Socket socket;
    private DataInputStream diStream;
    private DataOutputStream doStream;

    private Date date;
    private final AnalogueCommsLWA parent;
    private int id;

    public DedicatedLWA(Socket socket, AnalogueCommsLWA analogueCommsLWA, int id) {
        this.socket = socket;
        this.parent = analogueCommsLWA;
        date = new Date();
        this.id = id;
    }

    @Override
    public void run() {
        try {
            diStream = new DataInputStream(socket.getInputStream());
            doStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        while (true){
            try {
                String request = diStream.readUTF();
                actOnRequest(request);
            } catch (SocketException se){
                se.printStackTrace();
                System.exit(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void actOnRequest(String request) throws IOException {
        long timeStamp;
        int id;
        switch (request){
            case TMSTP_LWA1:
                System.out.println("[RECEIVING] TMSTP: " + TMSTP_LWA1);
                timeStamp = diStream.readLong();
                System.out.println("[RECEIVING] time: " + timeStamp);
                id = diStream.readInt();
                System.out.println("[RECEIVING] id: " + id);
                parent.addToQueue(timeStamp, TMSTP_LWA1, id);
                timeStamp = date.getTime();
                System.out.println("[RECEIVING - ANSWERING " + TMSTP_LWA1 + "] time: " + timeStamp);
                doStream.writeLong(timeStamp);
                System.out.println("[RECEIVING - ANSWERING " + TMSTP_LWA1 + "] id: " + this.id);
                doStream.writeInt(this.id);
                System.out.println("Answer to LWA1 done.");
                break;
            case TMSTP_LWA2:
                System.out.println("[RECEIVING] TMSTP: " + TMSTP_LWA2);
                timeStamp = diStream.readLong();
                System.out.println("[RECEIVING] time: " + timeStamp);
                id = diStream.readInt();
                System.out.println("[RECEIVING] id: " + id);
                parent.addToQueue(timeStamp, TMSTP_LWA2, id);
                timeStamp = date.getTime();
                System.out.println("[RECEIVING - ANSWERING " + TMSTP_LWA2 + "] time: " + timeStamp);
                doStream.writeLong(timeStamp);
                System.out.println("[RECEIVING - ANSWERING " + TMSTP_LWA2 + "] id: " + this.id);
                doStream.writeInt(this.id);
                System.out.println("Answer to LWA2 done.");
                break;
            case TMSTP_LWA3:
                System.out.println("[RECEIVING] TMSTP: " + TMSTP_LWA3);
                timeStamp = diStream.readLong();
                System.out.println("[RECEIVING] time: " + timeStamp);
                id = diStream.readInt();
                System.out.println("[RECEIVING] id: " + id);
                parent.addToQueue(timeStamp, TMSTP_LWA3, id);
                timeStamp = date.getTime();
                System.out.println("[RECEIVING - ANSWERING " + TMSTP_LWA3 + "] time: " + timeStamp);
                doStream.writeLong(timeStamp);
                System.out.println("[RECEIVING - ANSWERING " + TMSTP_LWA3 + "] id: " + this.id);
                doStream.writeInt(this.id);
                System.out.println("Answer to LWA3 done.");
                break;
            case "RELEASE":
                String releaseProcess = diStream.readUTF();
                System.out.println("Releasing process " + releaseProcess +  "...");
                parent.releaseRequest(releaseProcess);
                parent.checkCSAvailability();
                break;
        }
    }
}
