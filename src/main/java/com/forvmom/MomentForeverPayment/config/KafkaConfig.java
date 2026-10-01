package com.forvmom.MomentForeverPayment.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.listener.ContainerProperties;
import com.forvmom.MomentForeverPayment.events.PaymentRequestedEvent;
import com.forvmom.MomentForeverPayment.events.PaymentProcessedEvent;
import com.forvmom.MomentForeverPayment.events.PaymentFailedEvent;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

        @Value("${spring.kafka.bootstrap-servers}")
        private String bootstrapServers;

        @Value("${spring.kafka.consumer.group-id:payment-group}")
        private String groupId;

        @Value("${spring.kafka.listener.auto-startup:true}")
        private boolean listenerAutoStartup;

        // Producer Factory
        @Bean
        public ProducerFactory<String, Object> producerFactory() {
                Map<String, Object> config = new HashMap<>();
                config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
                config.put(ProducerConfig.ACKS_CONFIG, "all");
                config.put(ProducerConfig.RETRIES_CONFIG, 3);
                config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
                config.put(JsonSerializer.TYPE_MAPPINGS,
                        "com.forvmom.payment.events.PaymentProcessedEvent:"
                                + PaymentProcessedEvent.class.getName()
                                + ",com.forvmom.payment.events.PaymentFailedEvent:"
                                + PaymentFailedEvent.class.getName());
                return new DefaultKafkaProducerFactory<>(config);
        }

        @Bean
        public KafkaTemplate<String, Object> kafkaTemplate() {
                return new KafkaTemplate<>(producerFactory());
        }

        // Consumer Factory
        @Bean
        public ConsumerFactory<String, Object> consumerFactory() {
                Map<String, Object> config = new HashMap<>();
                config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
                config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
                config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
                config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
                config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
                config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
                config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                config.put(JsonDeserializer.TRUSTED_PACKAGES,
                        "com.forvmom.MomentForeverPayment.events");
                config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
                config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentRequestedEvent.class.getName());
                return new DefaultKafkaConsumerFactory<>(config);
        }

        @Bean
        public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
                ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
                factory.setConsumerFactory(consumerFactory());
                factory.setAutoStartup(listenerAutoStartup);
                factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
                return factory;
        }
}