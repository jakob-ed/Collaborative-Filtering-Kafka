package com.bakdata.demo;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.MockProcessorContext;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Demonstrate the use of {@link MockProcessorContext} for testing the {@link Processor} in the {@link WordCountProcessorDemo}.
 */
public class WordCountProcessorTest {
    @Test
    public void test() {
        final MockProcessorContext context = new MockProcessorContext();

        // Create, initialize, and register the state store.
        final KeyValueStore<String, Integer> store =
                Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore("Counts"), Serdes.String(), Serdes.Integer())
                        .withLoggingDisabled() // Changelog is not supported by MockProcessorContext.
                        .build();
        store.init(context, store);
        context.register(store, null);

        // Create and initialize the processor under test
        final Processor<String, String> processor = new WordCountProcessorDemo.MyProcessorSupplier().get();
        processor.init(context);

        // send a record to the processor
        processor.process("key", "alpha beta gamma alpha");

        // note that the processor commits, but does not forward, during process()
        assertTrue(context.committed());
        assertTrue(context.forwarded().isEmpty());

        // now, we trigger the punctuator, which iterates over the state store and forwards the contents.
        context.scheduledPunctuators().get(0).getPunctuator().punctuate(0L);

        // finally, we can verify the output.
        final Iterator<MockProcessorContext.CapturedForward> capturedForwards = context.forwarded().iterator();
        assertEquals(new KeyValue<>("alpha", "2"), capturedForwards.next().keyValue());
        assertEquals(new KeyValue<>("beta", "1"), capturedForwards.next().keyValue());
        assertEquals(new KeyValue<>("gamma", "1"), capturedForwards.next().keyValue());
        assertFalse(capturedForwards.hasNext());
    }
}