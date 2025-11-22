public class Pair<A, B> {
    public final A first;
    public final B second;
    public Pair(A a, B b) {
        first = a;
        second = b;
    }

    public static Pair<Integer, Integer> of(int a, int b) {
        return new Pair<>(a, b);
    }
}
