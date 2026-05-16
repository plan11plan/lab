package lab.deadlock.b.signup;

public class DuplicateSignupException extends RuntimeException {
    public DuplicateSignupException(String message) {
        super(message);
    }
}
