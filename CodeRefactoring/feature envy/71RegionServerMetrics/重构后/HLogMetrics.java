package org.apache.hadoop.hbase.regionserver.metrics;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.io.hfile.HFile;
import org.apache.hadoop.hbase.metrics.HBaseInfo;
import org.apache.hadoop.hbase.metrics.PersistentMetricsTimeVaryingRate;
import org.apache.hadoop.hbase.regionserver.wal.HLog;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.hbase.util.Strings;
import org.apache.hadoop.metrics.ContextFactory;
import org.apache.hadoop.metrics.MetricsContext;
import org.apache.hadoop.metrics.MetricsRecord;
import org.apache.hadoop.metrics.MetricsUtil;
import org.apache.hadoop.metrics.Updater;
import org.apache.hadoop.metrics.jvm.JvmMetrics;
import org.apache.hadoop.metrics.util.MetricsIntValue;
import org.apache.hadoop.metrics.util.MetricsLongValue;
import org.apache.hadoop.metrics.util.MetricsRegistry;
import org.apache.hadoop.metrics.util.MetricsTimeVaryingRate;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.List;

public class HLogMetrics {
    private MetricsContext metricsContext;

    public HLogMetrics(MetricsContext metricsContext) {
        this.metricsContext = metricsContext;
    }

    public void doUpdates() {
        int ops = (int)HLog.getWriteOps();
        if (ops != 0) this.metricsContext.fsWriteLatency.inc(ops, HLog.getWriteTime());
        ops = (int)HLog.getSyncOps();
        if (ops != 0) this.metricsContext.fsSyncLatency.inc(ops, HLog.getSyncTime());
        this.metricsContext.fsWriteLatency.pushMetric(this.metricsContext.metricsRecord);
        this.metricsContext.fsSyncLatency.pushMetric(this.metricsContext.metricsRecord);
    }
}
