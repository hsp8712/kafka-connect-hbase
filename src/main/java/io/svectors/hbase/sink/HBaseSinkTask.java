/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.svectors.hbase.sink;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.svectors.hbase.HBaseClient;
import io.svectors.hbase.HBaseConnectionFactory;
import io.svectors.hbase.util.ToPutFunction;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.Put;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.svectors.hbase.config.HBaseSinkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HBaseSinkTask extends SinkTask {

    private static final Logger LOG = LoggerFactory.getLogger(HBaseSinkTask.class);

    private ToPutFunction toPutFunction;
    private HBaseClient hBaseClient;
    private String hbaseTable;
    private long lastTime;

    @Override
    public String version() {
        return HBaseSinkConnector.VERSION;
    }

    @Override
    public void start(Map<String, String> props) {
        final HBaseSinkConfig sinkConfig = new HBaseSinkConfig(props);
        sinkConfig.validate(); // we need to do some sanity checks of the properties we configure.

        final String zookeeperQuorum = sinkConfig.getString(HBaseSinkConfig.ZOOKEEPER_QUORUM_CONFIG);
        final Configuration configuration = HBaseConfiguration.create();
        configuration.set(HConstants.ZOOKEEPER_QUORUM, zookeeperQuorum);

        final HBaseConnectionFactory connectionFactory = new HBaseConnectionFactory(configuration);
        this.hBaseClient = new HBaseClient(connectionFactory);
        this.toPutFunction = new ToPutFunction(sinkConfig);
        this.hbaseTable = sinkConfig.getString(HBaseSinkConfig.TABLE_NAME);

        this.lastTime = System.nanoTime();
    }

    @Override
    public void put(Collection<SinkRecord> records) {

        long currentTime = System.nanoTime();

        int diffSeconds = (int)((currentTime - lastTime) / (1000L * 1000L));
        if (diffSeconds >= 120) {
            LOG.info("Put size = {}.", records.size());
            lastTime = currentTime;
        }

        Map<String, List<SinkRecord>> byTopic =  records.stream()
          .collect(groupingBy(SinkRecord::topic));

        Map<String, List<Put>> byTable = byTopic.entrySet().stream()
          .collect(toMap(Map.Entry::getKey,
                         (e) -> e.getValue().stream().map(sr -> toPutFunction.apply(sr)).collect(toList())));

        byTable.entrySet().parallelStream().forEach(entry -> {
            hBaseClient.write(this.hbaseTable, entry.getValue());
        });
    }

    @Override
    public void flush(Map<TopicPartition, OffsetAndMetadata> offsets) {
        // NO-OP
    }

    @Override
    public void stop() {
        // NO-OP
    }

}
