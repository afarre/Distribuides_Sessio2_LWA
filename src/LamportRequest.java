public class LamportRequest {
    private long timeStamp;
    private String process;
    private int id;

    public LamportRequest(long timeStamp, String process, int id){
        this.timeStamp = timeStamp;
        this.process = process;
        this.id = id;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public String getProcess() {
        return process;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "LamportRequest{" +
                "timeStamp=" + timeStamp +
                ", process='" + process + '\'' +
                ", id=" + id +
                '}';
    }
}
