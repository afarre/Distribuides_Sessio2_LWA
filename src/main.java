import socket.S_LWA;
import xarxa.X_LWA;

public class main {
    public static void main(String[] args) {
        Menu menu = new Menu();
        int opcio = menu.showMenu();

        if (opcio == 1){
            S_LWA s_lwa = new S_LWA(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
            s_lwa.start();
        }else {
            X_LWA x_lwa = new X_LWA(args[0], Integer.parseInt(args[2]), Integer.parseInt(args[5]), Integer.parseInt(args[6]));
            x_lwa.start();
        }
    }
}