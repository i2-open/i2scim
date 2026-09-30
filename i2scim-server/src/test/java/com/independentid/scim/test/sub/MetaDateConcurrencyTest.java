/*
 * Copyright 2026.  Independent Identity Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.independentid.scim.test.sub;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.resource.Meta;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #110: {@code meta.created} / {@code meta.lastModified} formatting and parsing must be thread-safe, and the
 * serialised form must stay {@code yyyy-MM-dd'T'HH:mm:ss'Z'} (UTC, second precision).
 */
public class MetaDateConcurrencyTest {

    private static final int THREADS = 16;
    private static final int ITERATIONS = 2000;
    private static final String SCIM_DATE_REGEX = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";

    /** Independent reference formatter (immutable, thread-safe). */
    private static final DateTimeFormatter REFERENCE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    @Test
    public void serialisedFormatIsUnchanged() throws Exception {
        Instant instant = Instant.parse("2010-01-23T04:56:22Z");
        Meta meta = new Meta();
        meta.setCreatedDate(Date.from(instant));
        meta.setLastModifiedDate(Date.from(instant.plusSeconds(61)));

        assertThat(meta.getCreated()).isEqualTo("2010-01-23T04:56:22Z");
        assertThat(meta.getLastModified()).isEqualTo("2010-01-23T04:57:23Z");

        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(Meta.META_CREATED, "2010-01-23T04:56:22Z");
        node.put(Meta.META_LAST_MODIFIED, "2010-01-23T04:57:23Z");
        Meta parsed = new Meta(node);
        assertThat(parsed.getCreatedDate().toInstant()).isEqualTo(instant);
        assertThat(parsed.getLastModifiedDate().toInstant()).isEqualTo(instant.plusSeconds(61));
    }

    @Test
    public void concurrentMetaSerialiseAndParseIsCorrect() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();

        Instant base = Instant.parse("1995-06-15T00:00:00Z");
        for (int t = 0; t < THREADS; t++) {
            final int thread = t;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < ITERATIONS; i++) {
                        // Distinct, second-aligned instants spread across decades so any cross-thread corruption shows.
                        Instant created = base.plus((long) thread * 3_650L + i * 7L, ChronoUnit.DAYS)
                                .plusSeconds(i * 3_607L % 86_400L);
                        Instant modified = created.plusSeconds(thread * 13L + i);

                        Meta meta = new Meta();
                        meta.setCreatedDate(Date.from(created));
                        meta.setLastModifiedDate(Date.from(modified));

                        String cStr = meta.getCreated();
                        String mStr = meta.getLastModified();
                        if (!cStr.matches(SCIM_DATE_REGEX) || !cStr.equals(REFERENCE.format(created)))
                            errors.add("created format " + cStr + " != " + REFERENCE.format(created));
                        if (!mStr.matches(SCIM_DATE_REGEX) || !mStr.equals(REFERENCE.format(modified)))
                            errors.add("lastModified format " + mStr + " != " + REFERENCE.format(modified));

                        ObjectNode node = JsonNodeFactory.instance.objectNode();
                        node.put(Meta.META_CREATED, REFERENCE.format(created));
                        node.put(Meta.META_LAST_MODIFIED, REFERENCE.format(modified));
                        Meta parsed = new Meta(node);
                        if (parsed.getCreatedDate() == null || !parsed.getCreatedDate().toInstant().equals(created))
                            errors.add("created parse " + parsed.getCreatedDate() + " != " + created);
                        if (parsed.getLastModifiedDate() == null
                                || !parsed.getLastModifiedDate().toInstant().equals(modified))
                            errors.add("lastModified parse " + parsed.getLastModifiedDate() + " != " + modified);
                    }
                } catch (Throwable e) {
                    errors.add("exception: " + e);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures)
            f.get(2, TimeUnit.MINUTES);
        pool.shutdownNow();

        assertThat(errors).as("no errors from concurrent meta date handling (first few shown)")
                .isEmpty();
    }
}
