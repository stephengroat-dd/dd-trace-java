package datadog.trace.instrumentation.kafka_clients;

import static datadog.trace.agent.tooling.bytebuddy.matcher.ClassLoaderMatchers.hasClassNamed;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.core.datastreams.TagsProcessor.CONSUMER_GROUP_TAG;
import static datadog.trace.core.datastreams.TagsProcessor.KAFKA_CLUSTER_ID_TAG;
import static datadog.trace.core.datastreams.TagsProcessor.PARTITION_TAG;
import static datadog.trace.core.datastreams.TagsProcessor.TOPIC_TAG;
import static datadog.trace.core.datastreams.TagsProcessor.TYPE_TAG;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.InstrumenterModule;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.Metadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.internals.ConsumerCoordinator;
import org.apache.kafka.clients.consumer.internals.RequestFuture;
import org.apache.kafka.common.TopicPartition;

@AutoService(InstrumenterModule.class)
public final class ConsumerCoordinatorInstrumentation extends InstrumenterModule.Tracing
    implements Instrumenter.ForSingleType, Instrumenter.HasMethodAdvice {

  public ConsumerCoordinatorInstrumentation() {
    super("kafka", "kafka-0.11");
  }

  @Override
  public String muzzleDirective() {
    return "before-3.8";
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return not(hasClassNamed("org.apache.kafka.clients.MetadataRecoveryStrategy")); // < 3.8
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> contextStores = new HashMap<>();
    contextStores.put("org.apache.kafka.clients.Metadata", "java.lang.String");
    contextStores.put(
        "org.apache.kafka.clients.consumer.internals.ConsumerCoordinator",
        KafkaConsumerInfo.class.getName());
    return contextStores;
  }

  @Override
  public String instrumentedType() {
    return "org.apache.kafka.clients.consumer.internals.ConsumerCoordinator";
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".KafkaConsumerInfo"};
  }

  @Override
  public void methodAdvice(MethodTransformer transformer) {
    transformer.applyAdvice(
        isMethod().and(named("sendOffsetCommitRequest")).and(takesArguments(1)),
        ConsumerCoordinatorInstrumentation.class.getName() + "$CommitOffsetAdvice");
  }

  public static class CommitOffsetAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void trackCommitOffset(
        @Advice.This ConsumerCoordinator coordinator,
        @Advice.Return RequestFuture<Void> requestFuture,
        @Advice.Argument(0) final Map<TopicPartition, OffsetAndMetadata> offsets) {
      if (requestFuture == null || requestFuture.failed()) {
        return;
      }
      if (offsets == null) {
        return;
      }
      KafkaConsumerInfo kafkaConsumerInfo =
          InstrumentationContext.get(ConsumerCoordinator.class, KafkaConsumerInfo.class)
              .get(coordinator);

      if (kafkaConsumerInfo == null) {
        return;
      }

      String consumerGroup = kafkaConsumerInfo.getConsumerGroup();
      Metadata consumerMetadata = kafkaConsumerInfo.getClientMetadata();
      String clusterId = null;
      if (consumerMetadata != null) {
        clusterId = InstrumentationContext.get(Metadata.class, String.class).get(consumerMetadata);
      }

      for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : offsets.entrySet()) {
        if (consumerGroup == null) {
          consumerGroup = "";
        }
        if (entry.getKey() == null || entry.getValue() == null) {
          continue;
        }
        LinkedHashMap<String, String> sortedTags = new LinkedHashMap<>();
        sortedTags.put(CONSUMER_GROUP_TAG, consumerGroup);
        if (clusterId != null) {
          sortedTags.put(KAFKA_CLUSTER_ID_TAG, clusterId);
        }
        sortedTags.put(PARTITION_TAG, String.valueOf(entry.getKey().partition()));
        sortedTags.put(TOPIC_TAG, entry.getKey().topic());
        sortedTags.put(TYPE_TAG, "kafka_commit");
        AgentTracer.get()
            .getDataStreamsMonitoring()
            .trackBacklog(sortedTags, entry.getValue().offset());
      }
    }

    public static void muzzleCheck(ConsumerRecord record) {
      // KafkaConsumerInstrumentation only applies for kafka versions with headers
      // Make an explicit call so ConsumerCoordinatorInstrumentation does the same
      record.headers();
    }
  }
}
