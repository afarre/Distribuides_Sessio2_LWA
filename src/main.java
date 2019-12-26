public class main {
    public static void main(String[] args) {
        S_LWA s_lwa = new S_LWA(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), args[5], Integer.parseInt(args[6]));
        s_lwa.start();
    }
}