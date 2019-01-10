package Blockchain.Batch;

public interface BatcherProxy {
    void batch() throws InterruptedException;

    void interrupted();
}
