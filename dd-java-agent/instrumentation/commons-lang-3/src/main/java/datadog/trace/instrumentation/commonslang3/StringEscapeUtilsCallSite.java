package datadog.trace.instrumentation.commonslang3;

import datadog.trace.agent.tooling.csi.CallSite;
import datadog.trace.api.iast.IastCallSites;
import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.Propagation;
import datadog.trace.api.iast.VulnerabilityMarks;
import datadog.trace.api.iast.propagation.PropagationModule;
import javax.annotation.Nullable;

@Propagation
@CallSite(spi = IastCallSites.class)
public class StringEscapeUtilsCallSite {

  @CallSite.After(
      "java.lang.String org.apache.commons.lang3.StringEscapeUtils.escapeHtml3(java.lang.String)")
  @CallSite.After(
      "java.lang.String org.apache.commons.lang3.StringEscapeUtils.escapeHtml4(java.lang.String)")
  @CallSite.After(
      "java.lang.String org.apache.commons.lang3.StringEscapeUtils.escapeXml10(java.lang.String)")
  @CallSite.After(
      "java.lang.String org.apache.commons.lang3.StringEscapeUtils.escapeXml11(java.lang.String)")
  @CallSite.After(
      "java.lang.String org.apache.commons.lang3.StringEscapeUtils.escapeEcmaScript(java.lang.String)")
  public static String afterEscape(
      @CallSite.Argument(0) @Nullable final String input, @CallSite.Return final String result) {
    final PropagationModule module = InstrumentationBridge.PROPAGATION;
    if (module != null) {
      try {
        module.taintStringIfTainted(result, input, false, VulnerabilityMarks.HTML_ESCAPED_MARK);
      } catch (final Throwable e) {
        module.onUnexpectedException("afterEscape threw", e);
      }
    }
    return result;
  }

  @CallSite.After(
      "java.lang.String org.apache.commons.lang3.StringEscapeUtils.escapeJson(java.lang.String)")
  public static String afterEscapeJson(
      @CallSite.Argument(0) @Nullable final String input, @CallSite.Return final String result) {
    final PropagationModule module = InstrumentationBridge.PROPAGATION;
    if (module != null) {
      try {
        module.taintStringIfTainted(result, input);
      } catch (final Throwable e) {
        module.onUnexpectedException("afterEscapeJson threw", e);
      }
    }
    return result;
  }
}
