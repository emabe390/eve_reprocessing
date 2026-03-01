package emabe.eve.reprocessing;

public record Pair<A, B>(A first, B second) {

    public static Pair<Integer, Integer> of(int a, int b) {
        return new Pair<>(a, b);
    }
}
