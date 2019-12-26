import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;

public class S_LWA extends Thread {
    private int OUTGOING_HWA_PORT;
    private int FIRST_OUTGOING_PORT;
    private int SECOND_OUTGOING_PORT;
    private String TMSTP;

    private Socket socketHWA;
    private DataInputStream diStreamHWA;
    private DataOutputStream doStreamHWA;

    private Socket firstSocket;
    private DataInputStream firstDataInputStream;
    private DataOutputStream firstDataOutputStream;

    private Socket secondSocket;
    private DataInputStream secondDataInputStream;
    private DataOutputStream secondDataOutputStream;

    private AnalogueCommsLWA analogueCommsLWA;
    private int id;
    private int myPort;
    private String className;

    private boolean first;
    private boolean second;

    public S_LWA(String className, int outgoingHwaPort, int myPort, int firstOutgoingPort, int secondOutgoingPort, String time_stamp_lwa, int id){
        this.OUTGOING_HWA_PORT = outgoingHwaPort;
        this.FIRST_OUTGOING_PORT = firstOutgoingPort;
        this.SECOND_OUTGOING_PORT = secondOutgoingPort;
        this.id = id;
        this.className = className;
        this.myPort = myPort;
        this.TMSTP = time_stamp_lwa;
        first = false;
        second = false;
        analogueCommsLWA = new AnalogueCommsLWA(this, myPort, time_stamp_lwa, id);
        analogueCommsLWA.start();
    }

    @Override
    public synchronized void run() {
        try {
            connectToParent();
            doStreamHWA.writeUTF("ONLINE");
            doStreamHWA.writeUTF(className);
            boolean connect = diStreamHWA.readBoolean();

            if (connect){
                new DedicatedOutgoingSocket(this, FIRST_OUTGOING_PORT, SECOND_OUTGOING_PORT, TMSTP, analogueCommsLWA, id).start();
                //new DedicatedOutgoingSocket(SECOND_OUTGOING_PORT, SECOND_OUTGOING_PORT, TMSTP, analogueCommsLWA, id).start();
            }


/*
            boolean firstACK = firstDataInputStream.readBoolean();
            boolean secondACK = secondDataInputStream.readBoolean();

            if (firstACK && secondACK){
                System.out.println("----- STARTING " + className + " -----");

                while (true){
                    System.out.println("LWA esperant a missatge de HWA");
                    boolean signal = diStreamHWA.readBoolean();

                    if (signal){
                        System.out.println("got true in " + className);
                        firstDataOutputStream.writeUTF(TMSTP);
                        secondDataOutputStream.writeUTF(TMSTP);

                        long time = new java.util.Date().getTime();
                        firstDataOutputStream.writeLong(time);
                        analogueCommsLWA.addToQueue(time, TMSTP, id);
                        secondDataOutputStream.writeLong(time);
                        analogueCommsLWA.addToQueue(time, TMSTP, id);

                        System.out.println("Wrote to analogues from " + className + " at the time: " + time);

                        long firstResponseTimestamp = firstDataInputStream.readLong();
                        long secondResponseTimestamp = secondDataInputStream.readLong();

                        System.out.println("Received first timestamp in " + className + ": " + firstResponseTimestamp);
                        System.out.println("Received second timestamp in " + className + ": " + secondResponseTimestamp);

                        int firstId = firstDataInputStream.readInt();
                        int secondId = secondDataInputStream.readInt();

                        System.out.println("Received first ID in " + className + ": " + firstId);
                        System.out.println("Received second ID in " + className + ": " + secondId);
                        System.out.println("Queue process in " + className + ": " + analogueCommsLWA.peekQueue().getProcess());
                        System.out.println("Queue ID in " + className + ": " + analogueCommsLWA.peekQueue().getId());
                        System.out.println("TMSTP in " + className + ": " + TMSTP);

                        if (analogueCommsLWA.peekQueue().getProcess().equals(TMSTP)){
                            System.out.println("First IN in class: " + className);
                            if (firstResponseTimestamp > time && secondResponseTimestamp > time){
                                for (int i = 0; i < 10; i++){
                                    System.out.println("Soc el procés lightweight " + className);
                                    Thread.sleep(1000);
                                }
                            }else if (firstResponseTimestamp == time && secondResponseTimestamp == time && analogueCommsLWA.peekQueue().getId() < firstId && analogueCommsLWA.peekQueue().getId() < secondId){
                                for (int i = 0; i < 10; i++){
                                    System.out.println("Soc el procés lightweight " + className);
                                    Thread.sleep(1000);
                                }
                            }
                        }
                    }
                }
            }

 */
        } catch (ConnectException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connectToOtherLiveChilds() throws IOException {
        int numOfLiveChilds = diStreamHWA.readInt();
        System.out.println("Number of childs to connect to recieved: " + numOfLiveChilds);
        if (numOfLiveChilds == 2){
            String firstLiveChildName = diStreamHWA.readUTF();
            String secondLiveChildName = diStreamHWA.readUTF();
            System.out.println("M'han comunicat a " + className + " a quins childs m'he de conectar, que son: " + firstLiveChildName + " i " + secondLiveChildName);
            createSockets(firstLiveChildName);
            createSockets(secondLiveChildName);
            first = true;
            second = true;
        }else if (numOfLiveChilds == 1){
            String liveChildName = diStreamHWA.readUTF();
            System.out.println("M'han comunicat a " + className + " a quin child m'he de conectar, que es: " + liveChildName);
            createSockets(liveChildName);
            if (!first){
                first = true;
            }else {
                second = true;
            }
        }
        //TODO
    }

    private void connectToParent() throws IOException {
        InetAddress iAddress = InetAddress.getLocalHost();
        String IP = iAddress.getHostAddress();

        System.out.println(className + " connecting to parent");
        socketHWA = new Socket(String.valueOf(IP), OUTGOING_HWA_PORT);
        doStreamHWA = new DataOutputStream(socketHWA.getOutputStream());
        diStreamHWA = new DataInputStream(socketHWA.getInputStream());
    }

    private void createSockets(String child) throws IOException {
        InetAddress iAddress = InetAddress.getLocalHost();
        String IP = iAddress.getHostAddress();

        switch (child){
            case "LWA1":
                System.out.println("Connection to 55555");
                firstSocket = new Socket(String.valueOf(IP), 55555);
                firstDataOutputStream = new DataOutputStream(firstSocket.getOutputStream());
                firstDataInputStream = new DataInputStream(firstSocket.getInputStream());
                break;

            case "LWA2":
                System.out.println("Connection to 55556");
                if (className.equals("LWA1")){
                    firstSocket = new Socket(String.valueOf(IP), 55556);
                    firstDataOutputStream = new DataOutputStream(firstSocket.getOutputStream());
                    firstDataInputStream = new DataInputStream(firstSocket.getInputStream());
                }else if (className.equals("LWA3")){
                    secondSocket = new Socket(String.valueOf(IP), 55556);
                    secondDataOutputStream = new DataOutputStream(firstSocket.getOutputStream());
                    secondDataInputStream = new DataInputStream(firstSocket.getInputStream());
                }
                break;

            case "LWA3":
                System.out.println("Connection to 55557");
                secondSocket = new Socket(String.valueOf(IP), 55557);
                secondDataOutputStream = new DataOutputStream(firstSocket.getOutputStream());
                secondDataInputStream = new DataInputStream(firstSocket.getInputStream());
                break;
        }
/*
        System.out.println(className + " connecting to " + FIRST_OUTGOING_PORT);

        firstSocket = new Socket(String.valueOf(IP), FIRST_OUTGOING_PORT);
        firstDataOutputStream = new DataOutputStream(firstSocket.getOutputStream());
        firstDataInputStream = new DataInputStream(firstSocket.getInputStream());

        System.out.println(className + " connecting to " + SECOND_OUTGOING_PORT);

        secondSocket = new Socket(String.valueOf(IP), SECOND_OUTGOING_PORT);
        secondDataOutputStream = new DataOutputStream(secondSocket.getOutputStream());
        secondDataInputStream = new DataInputStream(secondSocket.getInputStream());
        System.out.println("All connections done");

 */
    }

    public void notifyDone() throws IOException {
        doStreamHWA.writeUTF("LWA1_DONE");
    }

    public void waitForFreeCS() {
        analogueCommsLWA.waitForFreeCS();
    }
}
