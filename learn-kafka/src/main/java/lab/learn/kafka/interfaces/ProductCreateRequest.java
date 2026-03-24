package lab.learn.kafka.interfaces;

public record ProductCreateRequest(
        String name,
        long price
) {
}
