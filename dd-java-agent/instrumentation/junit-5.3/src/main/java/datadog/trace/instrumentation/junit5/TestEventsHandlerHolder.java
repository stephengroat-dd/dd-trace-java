package datadog.trace.instrumentation.junit5;

import datadog.trace.api.civisibility.DDTest;
import datadog.trace.api.civisibility.DDTestSuite;
import datadog.trace.api.civisibility.InstrumentationBridge;
import datadog.trace.api.civisibility.events.TestEventsHandler;
import datadog.trace.api.civisibility.execution.TestExecutionHistory;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.util.AgentThreadFactory;
import org.junit.platform.engine.TestDescriptor;

public abstract class TestEventsHandlerHolder {

  public static volatile TestEventsHandler<TestDescriptor, TestDescriptor> TEST_EVENTS_HANDLER;
  private static ContextStore<TestDescriptor, DDTestSuite> SUITE_STORE;
  private static ContextStore<TestDescriptor, DDTest> TEST_STORE;
  private static volatile ContextStore<TestDescriptor, TestExecutionHistory>
      EXECUTION_HISTORY_STORE;

  static {
    Runtime.getRuntime()
        .addShutdownHook(
            AgentThreadFactory.newAgentThread(
                AgentThreadFactory.AgentThread.CI_TEST_EVENTS_SHUTDOWN_HOOK,
                TestEventsHandlerHolder::stop,
                false));
  }

  public static synchronized void setContextStores(
      ContextStore<TestDescriptor, DDTestSuite> suiteStore,
      ContextStore<TestDescriptor, DDTest> testStore) {
    if (SUITE_STORE == null) {
      SUITE_STORE = suiteStore;
    }
    if (TEST_STORE == null) {
      TEST_STORE = testStore;
    }
  }

  public static synchronized void setExecutionHistoryStore(
      ContextStore<TestDescriptor, TestExecutionHistory> executionHistoryStore) {
    if (EXECUTION_HISTORY_STORE == null) {
      EXECUTION_HISTORY_STORE = executionHistoryStore;
    }
  }

  public static void setExecutionHistory(
      TestDescriptor testDescriptor, TestExecutionHistory history) {
    if (EXECUTION_HISTORY_STORE != null) {
      EXECUTION_HISTORY_STORE.put(testDescriptor, history);
    }
  }

  public static TestExecutionHistory getExecutionHistory(TestDescriptor testDescriptor) {
    if (EXECUTION_HISTORY_STORE != null) {
      return EXECUTION_HISTORY_STORE.get(testDescriptor);
    } else {
      return null;
    }
  }

  public static synchronized void start() {
    if (TEST_EVENTS_HANDLER == null) {
      TEST_EVENTS_HANDLER =
          InstrumentationBridge.createTestEventsHandler("junit", SUITE_STORE, TEST_STORE);
    }
  }

  // used by instrumentation tests
  public static synchronized void startForcefully() {
    if (SUITE_STORE != null && TEST_STORE != null) {
      TEST_EVENTS_HANDLER =
          InstrumentationBridge.createTestEventsHandler("junit", SUITE_STORE, TEST_STORE);
    }
  }

  public static synchronized void stop() {
    if (TEST_EVENTS_HANDLER != null) {
      TEST_EVENTS_HANDLER.close();
      TEST_EVENTS_HANDLER = null;
    }
  }

  private TestEventsHandlerHolder() {}
}
