package com.lightspeedhq.pubsubmessageorderingproblem;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.spring.autoconfigure.core.GcpContextAutoConfiguration;
import com.google.cloud.spring.autoconfigure.pubsub.GcpPubSubAutoConfiguration;
import com.google.cloud.spring.autoconfigure.pubsub.GcpPubSubEmulatorAutoConfiguration;
import com.google.cloud.spring.pubsub.PubSubAdmin;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.pubsub.v1.Subscription;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PubSubEmulatorContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.google.cloud.spring.pubsub.support.GcpPubSubHeaders.ORDERING_KEY;
import static com.google.cloud.spring.pubsub.support.GcpPubSubHeaders.ORIGINAL_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@ImportAutoConfiguration(classes = {
        GcpPubSubAutoConfiguration.class,
        GcpPubSubEmulatorAutoConfiguration.class,
        GcpContextAutoConfiguration.class,
        IntegrationAutoConfiguration.class
})
@SpringBootTest(classes = PubSubOrderingIntegrationTest.SubscriberConfig.class)
class PubSubOrderingIntegrationTest {
    private static final List<String> MESSAGES_IN_RECEIPT_ORDER = Collections.synchronizedList(new ArrayList<>());
    private static final String SUBSCRIPTION_NAME = "test-subscription";
    private static final String TOPIC_NAME = "test-topic";
    static GenericContainer<?> pubsubContainer =
            new PubSubEmulatorContainer("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators");

    @Autowired
    PubSubInboundChannelAdapter inboundChannelAdapter;
    @Autowired
    PubSubAdmin pubSubAdmin;
    @MockBean
    CredentialsProvider credentials;
    @Autowired
    private PubSubTemplate pubSubTemplate;

    @BeforeAll
    static void beforeAll() {
        pubsubContainer.start();
    }

    @AfterAll
    static void afterAll() {
        pubsubContainer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gcp.pubsub.publisher.enable-message-ordering", () -> "true");
        registry.add("spring.cloud.gcp.pubsub.project-id", () -> "emulator");
        registry.add("spring.cloud.gcp.credentials.encoded-key", () -> "");
        registry.add("spring.cloud.gcp.pubsub.emulator-host", () -> "localhost:" + pubsubContainer.getMappedPort(8085));
    }

    @BeforeEach
    void setup() {
        pubSubAdmin.createTopic(TOPIC_NAME);
        pubSubAdmin.createSubscription(Subscription.newBuilder()
                .setName(SUBSCRIPTION_NAME)
                .setTopic(TOPIC_NAME)
                .setEnableMessageOrdering(true)
        );
    }

    @Test
    @SneakyThrows
    void testMessagesAreReceivedInPublicationOrder() {
        final int messageCount = 100;
        var orderingKeyHeader = Map.of(ORDERING_KEY, "messages_for_same_key_must_arrive_in_order");
        var should = IntStream.range(0, messageCount).mapToObj(Integer::toString).toList();
        for (var m : should) {
            pubSubTemplate.publish(TOPIC_NAME, m, orderingKeyHeader).get();
        }
        inboundChannelAdapter.start();
        await().until(() -> MESSAGES_IN_RECEIPT_ORDER.size() == messageCount);
        assertEquals(should, MESSAGES_IN_RECEIPT_ORDER);
    }

    @Slf4j
    static class SubscriberConfig {
        // Create a message channel for messages arriving from the subscription `sub-one`.
        @Bean
        public MessageChannel inputMessageChannel() {
            return new PublishSubscribeChannel();
        }

        // Create an inbound channel adapter to listen to the subscription `sub-one` and send
        // messages to the input message channel.
        @Bean
        public PubSubInboundChannelAdapter inboundChannelAdapter(
                @Qualifier("inputMessageChannel") MessageChannel messageChannel,
                PubSubTemplate pubSubTemplate) {
            var adapter = new PubSubInboundChannelAdapter(pubSubTemplate, SUBSCRIPTION_NAME);
            adapter.setOutputChannel(messageChannel);
            adapter.setAckMode(AckMode.MANUAL);
            adapter.setAutoStartup(false);
            return adapter;
        }

        // Define what happens to the messages arriving in the message channel.
        @ServiceActivator(inputChannel = "inputMessageChannel")
        @SneakyThrows
        public void messageReceiver(
                String payload,
                @Header(ORIGINAL_MESSAGE) BasicAcknowledgeablePubsubMessage message) {
            message.ack().get();
            MESSAGES_IN_RECEIPT_ORDER.add(payload);
        }
    }
}
