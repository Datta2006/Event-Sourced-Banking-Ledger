package com.bank.ledgerquery.service;

import com.bank.ledgerquery.config.ShardedLedger;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/** System Ops facade: Eureka registry, physical shards, Kafka topics and lag. */
@Service
public class SystemOpsService {

    private static final Logger log = LoggerFactory.getLogger(SystemOpsService.class);

    private final DiscoveryClient discoveryClient;
    private final ShardedLedger shardedLedger;
    private final String bootstrapServers;

    public SystemOpsService(DiscoveryClient discoveryClient, ShardedLedger shardedLedger,
                            @Value("${spring.kafka.bootstrap-servers:localhost:29092}") String bootstrapServers) {
        this.discoveryClient = discoveryClient;
        this.shardedLedger = shardedLedger;
        this.bootstrapServers = bootstrapServers;
    }

    public List<Map<String, Object>> services() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String serviceId : new TreeSet<>(discoveryClient.getServices())) {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
            if (instances.isEmpty()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("serviceId", serviceId);
                row.put("instanceId", serviceId + ":0");
                row.put("host", "unknown");
                row.put("port", 0);
                row.put("status", "DOWN");
                result.add(row);
            }
            for (ServiceInstance instance : instances) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("serviceId", serviceId);
                row.put("instanceId", instance.getInstanceId() == null
                        ? serviceId + ":" + instance.getPort() : instance.getInstanceId());
                row.put("host", instance.getHost());
                row.put("port", instance.getPort());
                row.put("status", "UP");
                row.put("registeredAt", OffsetDateTime.now().toString());
                result.add(row);
            }
        }
        return result;
    }

    public List<Map<String, Object>> shards() {
        return shardedLedger.stats();
    }

    public List<Map<String, Object>> kafkaTopics() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("request.timeout.ms", "5000");
        props.put("default.api.timeout.ms", "5000");
        List<Map<String, Object>> result = new ArrayList<>();
        try (Admin admin = Admin.create(props)) {
            Set<String> names = admin.listTopics().names().get(5, TimeUnit.SECONDS);
            Map<String, TopicDescription> described = admin.describeTopics(names)
                    .allTopicNames().get(5, TimeUnit.SECONDS);
            Map<String, Map<String, Long>> lag = consumerLag(admin);

            for (String name : new TreeSet<>(names)) {
                if (name.startsWith("__")) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("topic", name);
                TopicDescription description = described.get(name);
                row.put("partitions", description == null ? 0 : description.partitions().size());
                List<Map<String, Object>> groups = new ArrayList<>();
                lag.getOrDefault(name, Map.of()).forEach((groupId, value) -> {
                    Map<String, Object> group = new LinkedHashMap<>();
                    group.put("groupId", groupId);
                    group.put("lag", value);
                    groups.add(group);
                });
                row.put("groups", groups);
                result.add(row);
            }
        } catch (Exception e) {
            log.warn("Could not describe Kafka topics at {}: {}", bootstrapServers, e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("topic", "_unavailable");
            error.put("partitions", 0);
            error.put("groups", List.of());
            result.add(error);
        }
        return result;
    }

    /** topic → group → summed lag. */
    private Map<String, Map<String, Long>> consumerLag(Admin admin) throws Exception {
        Map<String, Map<String, Long>> byTopic = new TreeMap<>();
        java.util.Collection<ConsumerGroupListing> groups =
                admin.listConsumerGroups().valid().get(5, TimeUnit.SECONDS);
        for (ConsumerGroupListing group : groups) {
            Map<TopicPartition, OffsetAndMetadata> committed;
            try {
                committed = admin.listConsumerGroupOffsets(group.groupId())
                        .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                continue;
            }
            if (committed.isEmpty()) {
                continue;
            }
            Map<TopicPartition, OffsetSpec> request = new TreeMap<>();
            committed.keySet().forEach(tp -> request.put(tp, OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends =
                    admin.listOffsets(request).all().get(5, TimeUnit.SECONDS);
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committed.entrySet()) {
                long end = ends.containsKey(entry.getKey()) ? ends.get(entry.getKey()).offset() : 0;
                long value = Math.max(0, end - entry.getValue().offset());
                byTopic.computeIfAbsent(entry.getKey().topic(), k -> new TreeMap<>())
                        .merge(group.groupId(), value, Long::sum);
            }
        }
        return byTopic;
    }
}
