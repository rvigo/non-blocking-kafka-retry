package com.kafka.retry.configurations.kafka.resolvers;

import com.kafka.retry.configurations.kafka.managers.KafkaTopicHolder;
import com.kafka.retry.configurations.kafka.utils.KafkaExceptionRecover;
import com.kafka.retry.configurations.kafka.utils.Properties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;

@Slf4j
@Component
@AllArgsConstructor
public class KafkaTopicDestinationResolver {
    private final Properties kafkaProperties;
    private final KafkaTopicHolder kafkaTopicHolder;
    private final KafkaExceptionRecover kafkaExceptionRecover;


    // resolve topic destination for the main consumer
    public BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> resolveMainTopicDestination() {
        return (record, exception) -> {
            if (kafkaExceptionRecover.isRecoverableException(exception)) {
                String source = record.topic() != null && !record.topic().isEmpty() ? record.topic() : kafkaTopicHolder.getFirstTopic().getTopicName();
                String target = kafkaTopicHolder.getKafkaTopicByName(source).getNextTopic().getTopicName();
                return new TopicPartition(target, record.partition());
            }
            log.debug(String.format("sending message with id %s to dlq ", record.key()));
            return new TopicPartition(kafkaTopicHolder.getLastTopic().getTopicName(), record.partition());
        };
    }

    //resolve topic destination for retries consumer
    public BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> resolveRetryTopicDestination() {
        return (record, exception) -> {
            String target;
            int actualRetryCount = kafkaTopicHolder.getKafkaTopicByName(record.topic()).getRetryValue();
            if (actualRetryCount < kafkaProperties.getMaxRetries()) {
                target = kafkaTopicHolder.getKafkaTopicByRetryValue(actualRetryCount).getNextTopic().getTopicName();
            } else {
                log.debug(String.format("sending message %s to dlq ", record.key()));
                target = kafkaTopicHolder.getLastTopic().getTopicName();
            }
            return new TopicPartition(target, record.partition());
        };
    }
}