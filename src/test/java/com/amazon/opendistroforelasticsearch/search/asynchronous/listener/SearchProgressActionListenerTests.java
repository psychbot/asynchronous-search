/*
 *   Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License").
 *   You may not use this file except in compliance with the License.
 *   A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file. This file is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *   express or implied. See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package com.amazon.opendistroforelasticsearch.search.asynchronous.listener;

import com.amazon.opendistroforelasticsearch.search.asynchronous.commons.AsynchronousSearchTestCase;
import com.amazon.opendistroforelasticsearch.search.asynchronous.response.AsynchronousSearchResponse;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.internal.InternalSearchResponse;
import org.elasticsearch.search.profile.SearchProfileShardResults;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.threadpool.TestThreadPool;
import org.elasticsearch.threadpool.ThreadPool;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;


public class SearchProgressActionListenerTests extends AsynchronousSearchTestCase {

    private final AtomicReference<SearchResponse> responseRef = new AtomicReference<>();
    private final AtomicReference<Exception> exceptionRef = new AtomicReference<>();
    private Exception mockSearchException;
    private RuntimeException mockPostProcessingException;
    private SearchResponse mockSearchResponse;
    private AsynchronousSearchResponse mockAsynchronousSearchResp;
    private AsynchronousSearchResponse mockAsynchronousSearchFailResp;

    @Before
    public void setUpMocks() {
        mockSearchException = new RuntimeException("random-search-exception");
        mockPostProcessingException = new RuntimeException("random-post-processing-exception");
        mockSearchResponse = new SearchResponse(new InternalSearchResponse(
                new SearchHits(new SearchHit[0], new TotalHits(0L, TotalHits.Relation.EQUAL_TO), 0.0f),
                InternalAggregations.from(Collections.emptyList()),
                new Suggest(Collections.emptyList()),
                new SearchProfileShardResults(Collections.emptyMap()), false, false, 1),
                "", 1, 1, 0, 0, ShardSearchFailure.EMPTY_ARRAY,
                SearchResponse.Clusters.EMPTY);

        mockAsynchronousSearchResp = AsynchronousSearchResponse.empty("random-id", mockSearchResponse, null);
        mockAsynchronousSearchFailResp = AsynchronousSearchResponse.empty("random-id", null,
                new ElasticsearchException(mockSearchException));
    }

    public void testListenerOnResponseForSuccessfulSearch() throws InterruptedException {
        TestThreadPool threadPool = null;
        try {
            final int numListeners = randomIntBetween(1, 20);
            threadPool = new TestThreadPool(getClass().getName());
            Function<SearchResponse, AsynchronousSearchResponse> responseFunction =
                    (r) -> {
                        assertTrue(responseRef.compareAndSet(null, r));
                        return mockAsynchronousSearchResp;
                    };
            Function<Exception, AsynchronousSearchResponse> failureFunction =
                    (e) -> {
                        assertTrue(exceptionRef.compareAndSet(null, e));
                        return mockAsynchronousSearchFailResp;
                    };
            AsynchronousSearchProgressListener progressActionListener = mockAsynchronousSearchProgressListener(threadPool, responseFunction,
                    failureFunction);
            Tuple<List<AtomicReference<AsynchronousSearchResponse>>, List<AtomicReference<Exception>>> respTuple =
                    processListeners(progressActionListener, () -> progressActionListener.onResponse(mockSearchResponse), numListeners);

            List<AtomicReference<AsynchronousSearchResponse>> responseList = respTuple.v1();
            List<AtomicReference<Exception>> exceptionList = respTuple.v2();
            //assert all response listeners that were added were invoked
            assertEquals(numListeners, responseList.size());
            assertEquals(0, exceptionList.size());
            assertNull(exceptionRef.get());
            assertEquals(mockSearchResponse, responseRef.get());

            for (int i = 0; i < numListeners; i++) {
                //assert all response listeners that were added were invoked with the search response
                assertEquals(mockAsynchronousSearchResp, responseList.get(i).get());
            }
        } finally {
            ThreadPool.terminate(threadPool, 100, TimeUnit.MILLISECONDS);
        }
    }

    public void testListenerOnResponseForFailedSearch() throws InterruptedException {
        TestThreadPool threadPool = null;
        try {
            final int numListeners = randomIntBetween(1, 20);
            threadPool = new TestThreadPool(getClass().getName());
            Function<SearchResponse, AsynchronousSearchResponse> responseFunction =
                    (r) -> {
                        assertTrue(responseRef.compareAndSet(null, r));
                        return mockAsynchronousSearchResp;
                    };
            Function<Exception, AsynchronousSearchResponse> failureFunction =
                    (e) -> {
                        assertTrue(exceptionRef.compareAndSet(null, e));
                        return mockAsynchronousSearchFailResp;
                    };
            AsynchronousSearchProgressListener progressActionListener = mockAsynchronousSearchProgressListener(threadPool, responseFunction,
                    failureFunction);
            Tuple<List<AtomicReference<AsynchronousSearchResponse>>, List<AtomicReference<Exception>>> respTuple =
                    processListeners(progressActionListener, () -> progressActionListener.onFailure(mockSearchException), numListeners);

            List<AtomicReference<AsynchronousSearchResponse>> responseList = respTuple.v1();
            List<AtomicReference<Exception>> exceptionList = respTuple.v2();
            //assert all response listeners that were added were invoked
            assertEquals(numListeners, responseList.size());
            assertEquals(0, exceptionList.size());
            assertEquals(mockSearchException, exceptionRef.get());
            assertNull(responseRef.get());

            for (int i = 0; i < numListeners; i++) {
                //assert all response listeners that were added were invoked with the search response
                assertEquals(mockAsynchronousSearchFailResp, responseList.get(i).get());
            }
        } finally {
            ThreadPool.terminate(threadPool, 100, TimeUnit.MILLISECONDS);
        }
    }

    public void testListenerOnFailureForFailedSearch() throws InterruptedException {
        TestThreadPool threadPool = null;
        try {
            final int numListeners = randomIntBetween(1, 20);
            threadPool = new TestThreadPool(getClass().getName());
            Function<SearchResponse, AsynchronousSearchResponse> responseFunction =
                    (r) -> {
                        assertTrue(responseRef.compareAndSet(null, r));
                        throw mockPostProcessingException;
                    };
            Function<Exception, AsynchronousSearchResponse> failureFunction =
                    (e) -> {
                        assertTrue(exceptionRef.compareAndSet(null, e));
                        throw mockPostProcessingException;
                    };
            AsynchronousSearchProgressListener progressActionListener = mockAsynchronousSearchProgressListener(threadPool, responseFunction,
                    failureFunction);
            Tuple<List<AtomicReference<AsynchronousSearchResponse>>, List<AtomicReference<Exception>>> respTuple =
                    processListeners(progressActionListener, () -> progressActionListener.onFailure(mockSearchException), numListeners);

            List<AtomicReference<AsynchronousSearchResponse>> responseList = respTuple.v1();
            List<AtomicReference<Exception>> exceptionList = respTuple.v2();
            //assert all response listeners that were added were invoked
            assertEquals(0, responseList.size());
            assertEquals(numListeners, exceptionList.size());
            assertEquals(mockSearchException, exceptionRef.get());
            assertEquals(null, responseRef.get());

            for (int i = 0; i < numListeners; i++) {
                //assert all response listeners that were added were invoked with the search response
                assertEquals(mockPostProcessingException, exceptionList.get(i).get());
            }
        } finally {
            ThreadPool.terminate(threadPool, 100, TimeUnit.MILLISECONDS);
        }
    }

    public void testListenerOnFailureForSuccessfulSearch() throws InterruptedException {
        TestThreadPool threadPool = null;
        try {
            final int numListeners = randomIntBetween(1, 20);
            threadPool = new TestThreadPool(getClass().getName());
            Function<SearchResponse, AsynchronousSearchResponse> responseFunction =
                    (r) -> {
                        assertTrue(responseRef.compareAndSet(null, r));
                        throw mockPostProcessingException;
                    };
            Function<Exception, AsynchronousSearchResponse> failureFunction =
                    (e) -> {
                        assertTrue(exceptionRef.compareAndSet(null, e));
                        throw mockPostProcessingException;
                    };
            AsynchronousSearchProgressListener progressActionListener = mockAsynchronousSearchProgressListener(threadPool, responseFunction,
                    failureFunction);
            Tuple<List<AtomicReference<AsynchronousSearchResponse>>, List<AtomicReference<Exception>>> respTuple =
                    processListeners(progressActionListener, () -> progressActionListener.onResponse(mockSearchResponse), numListeners);

            List<AtomicReference<AsynchronousSearchResponse>> responseList = respTuple.v1();
            List<AtomicReference<Exception>> exceptionList = respTuple.v2();
            //assert all response listeners that were added were invoked
            assertEquals(0, responseList.size());
            assertEquals(numListeners, exceptionList.size());
            assertNull(exceptionRef.get());
            assertEquals(mockSearchResponse, responseRef.get());

            for (int i = 0; i < numListeners; i++) {
                //assert all response listeners that were added were invoked with the search response
                assertEquals(mockPostProcessingException, exceptionList.get(i).get());
            }
        } finally {
            ThreadPool.terminate(threadPool, 100, TimeUnit.MILLISECONDS);
        }
    }

    public Tuple<List<AtomicReference<AsynchronousSearchResponse>>, List<AtomicReference<Exception>>> processListeners(
            AsynchronousSearchProgressListener progressActionListener, Runnable listenerAction,
            int numListeners) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(numListeners);
        final List<AtomicReference<AsynchronousSearchResponse>> responseList = new ArrayList<>();
        final List<AtomicReference<Exception>> exceptionList = new ArrayList<>();
        final AtomicInteger immediateExecution = new AtomicInteger();

        for (int i = 0; i < numListeners; i++) {
            progressActionListener.searchProgressActionListener().addOrExecuteListener(createMockListener(responseList, exceptionList,
                    immediateExecution, latch));
        }
        listenerAction.run();
        //wait for all listeners to be executed since on response is forked to a separate thread pool
        latch.await();
        return new Tuple<>(responseList, exceptionList);
    }


    private PrioritizedActionListener<AsynchronousSearchResponse> createMockListener(
            List<AtomicReference<AsynchronousSearchResponse>> responseList,
            List<AtomicReference<Exception>> exceptionList,
            AtomicInteger immediateExecution,
            CountDownLatch latch) {

        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicReference<AsynchronousSearchResponse> asResponseRef = new AtomicReference<>();
        final AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        return new PrioritizedActionListener<AsynchronousSearchResponse>() {
            @Override
            public void executeImmediately() {
                assertTrue(completed.compareAndSet(false, true));
                immediateExecution.incrementAndGet();
                latch.countDown();
            }

            @Override
            public void onResponse(AsynchronousSearchResponse asResponse) {
                assertTrue(completed.compareAndSet(false, true));
                assertTrue(asResponseRef.compareAndSet(null, asResponse));
                responseList.add(asResponseRef);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                assertTrue(completed.compareAndSet(false, true));
                exceptionRef.compareAndSet(null, e);
                exceptionList.add(exceptionRef);
                latch.countDown();
            }
        };
    }
}
