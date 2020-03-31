public class LamportRequest {
    private int clock;
    private String process;
    private int id;

    public LamportRequest(int clock, String process, int id){
        this.clock = clock;
        this.process = process;
        this.id = id;
    }

    public int getClock() {
        return clock;
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
                "clock=" + clock +
                ", process='" + process + '\'' +
                ", id=" + id +
                '}';
    }
}
