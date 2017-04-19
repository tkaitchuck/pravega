package com.emc.pravega.stream.lowlevel.impl;

import com.emc.pravega.common.concurrent.FutureHelpers;
import com.emc.pravega.common.netty.ConnectionFailedException;
import com.emc.pravega.common.netty.FailingReplyProcessor;
import com.emc.pravega.common.netty.PravegaNodeUri;
import com.emc.pravega.common.netty.WireCommands.GetStreamSegmentInfo;
import com.emc.pravega.common.netty.WireCommands.NoSuchSegment;
import com.emc.pravega.common.netty.WireCommands.StreamSegmentInfo;
import com.emc.pravega.common.netty.WireCommands.WrongHost;
import com.emc.pravega.common.util.Retry;
import com.emc.pravega.stream.Segment;
import com.emc.pravega.stream.impl.Controller;
import com.emc.pravega.stream.impl.netty.ClientConnection;
import com.emc.pravega.stream.impl.netty.ConnectionFactory;
import com.emc.pravega.stream.lowlevel.SegmentManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.annotation.concurrent.GuardedBy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SegmentManagerImpl implements SegmentManager {
    private final Object lock = new Object();
    private final ResponseProcessor responseProcessor = new ResponseProcessor();
    @GuardedBy("lock")
    private final Map<Long, CompletableFuture<StreamSegmentInfo>> infoRequests = new HashMap<>();
    private final Supplier<Long> infoRequestIdGenerator = new AtomicLong()::incrementAndGet;
    @GuardedBy("lock")
    private CompletableFuture<ClientConnection> connection = null;
    @GuardedBy("lock")
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ScheduledThreadPoolExecutor threadPool;
    private final ConnectionFactory connectionFactory;
    private final Controller controller;
    private Segment segment;

    public SegmentManagerImpl(ScheduledThreadPoolExecutor threadPool, ConnectionFactory connectionFactory,
            Controller controller, Segment segment) {
        super();
        this.threadPool = threadPool;
        this.connectionFactory = connectionFactory;
        this.controller = controller;
        this.segment = segment;
    }

    private final class ResponseProcessor extends FailingReplyProcessor {

        @Override
        public void streamSegmentInfo(StreamSegmentInfo streamInfo) {
            log.trace("Received stream segment info {}", streamInfo);
            CompletableFuture<StreamSegmentInfo> future;
            synchronized (lock) {
                future = infoRequests.remove(streamInfo.getRequestId());
            }
            if (future != null) {
                future.complete(streamInfo);
            }
        }

        @Override
        public void connectionDropped() {
            closeConnection(new ConnectionFailedException());
        }

        @Override
        public void wrongHost(WrongHost wrongHost) {
            closeConnection(new ConnectionFailedException(wrongHost.toString()));
        }

        @Override
        public void noSuchSegment(NoSuchSegment noSuchSegment) {
            // TODO: It's not clear how we should be handling this case. (It should be
            // impossible...)
            closeConnection(new IllegalArgumentException(noSuchSegment.toString()));
        }

    }

    CompletableFuture<ClientConnection> getConnection() {
        synchronized (lock) {
            // Optimistic check
            if (connection != null) {
                return connection;
            }
        }
        return Retry.withExpBackoff(10, 10, 4, 10000)
                    .retryingOn(RuntimeException.class)
                    .throwingOn(Exception.class)
                    .runAsync(() -> {
                        return controller.getEndpointForSegment(segment.getScopedName())
                                         .thenComposeAsync((PravegaNodeUri uri) -> {
                                             synchronized (lock) {
                                                 if (connection == null) {
                                                     connection = connectionFactory.establishConnection(uri,
                                                             responseProcessor);
                                                 }
                                                 return connection;
                                             }
                                         }, threadPool);
                    }, threadPool);
    }

    private void closeConnection(Exception exceptionToInflightRequests) {
        log.trace("Closing connection with exception: {}", exceptionToInflightRequests.toString());
        CompletableFuture<ClientConnection> c;
        synchronized (lock) {
            c = connection;
            connection = null;
        }
        if (c != null && FutureHelpers.isSuccessful(c)) {
            try {
                c.getNow(null).close();
            } catch (Exception e) {
                log.warn("Exception tearing down connection: ", e);
            }
        }
        failInfoRequests(exceptionToInflightRequests);
    }

    private void failInfoRequests(Exception e) {
        if (!closed.get()) {
            log.info("Connection failed due to a {}. ", e.toString());
        }
        List<CompletableFuture<StreamSegmentInfo>> infoRequestsToFail;
        synchronized (lock) {
            infoRequestsToFail = new ArrayList<>(infoRequests.values());
            infoRequests.clear();
        }
        for (CompletableFuture<StreamSegmentInfo> infoRequest : infoRequestsToFail) {
            infoRequest.completeExceptionally(e);
        }
    }

    private CompletableFuture<StreamSegmentInfo> getSegmentInfo() {
        CompletableFuture<StreamSegmentInfo> result = new CompletableFuture<>();
        long requestId = infoRequestIdGenerator.get();
        synchronized (lock) {
            infoRequests.put(requestId, result);
        }
        getConnection().thenAccept(c -> {
            try {
                log.trace("Getting segment info");
                c.send(new GetStreamSegmentInfo(requestId, segment.getScopedName()));
            } catch (ConnectionFailedException e) {
                closeConnection(e);
            }
        });
        return result;
    }

    @Override
    public StreamSegmentInfo getSegmentInfo(Segment segment) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void createSegment(Segment segment) {
        // TODO Auto-generated method stub

    }

    @Override
    public void sealSegment(Segment segment) {
        // TODO Auto-generated method stub

    }

    @Override
    public void truncateSegment(Segment segment, long upToOffset) {
        // TODO Auto-generated method stub

    }

    @Override
    public void deleteSegment(Segment segment) {
        // TODO Auto-generated method stub

    }

}
