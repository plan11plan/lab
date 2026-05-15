package lab.support;

public record ConcurrencyResult(int success, int fail) {
    public int total() { return success + fail; }
}
