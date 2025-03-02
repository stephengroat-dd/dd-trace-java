import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TriggerTask implements Runnable {

  private final CountDownLatch latch = new CountDownLatch(1);

  @Scheduled(cron = "0/5 * * * * *", scheduler = "tracingTaskScheduler")
  @Override
  public void run() {
    latch.countDown();
  }

  public void blockUntilExecute() throws InterruptedException {
    latch.await(5, TimeUnit.SECONDS);
  }
}
