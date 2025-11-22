public class Debug {
    public static boolean DEBUG = false;
    public static boolean TIMESTAMP = true;


    public static void print(Object o) {
        print(o.toString());
    }

    public static void print(String s) {
        if (DEBUG) {
            if (TIMESTAMP) {
                System.out.println(System.currentTimeMillis() + ": " + s);
            } else {
                System.out.println(s);
            }
        }
    }
}
