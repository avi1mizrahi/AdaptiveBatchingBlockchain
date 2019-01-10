package Blockchain.Batch;

import java.time.Duration;

public class FixedWindowBatching extends TimedAdaptiveBatching {

    FixedWindowBatching(Duration blockWindow) {
        super(blockWindow, 1);
    }
}
