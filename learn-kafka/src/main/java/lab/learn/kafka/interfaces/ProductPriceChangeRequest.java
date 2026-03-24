package lab.learn.kafka.interfaces;

public record ProductPriceChangeRequest(
        long newPrice
) {
}
