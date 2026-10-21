package fu.ats.exception;

public class JobRequestRejectedException extends RuntimeException {
    private final int status;

    public JobRequestRejectedException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
