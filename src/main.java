public class main {
    public static void main(String[] args) {
        S_LWA s_lwa = new S_LWA(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
        s_lwa.start();
    }
}