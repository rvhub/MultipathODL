/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.protocol.framework;

import com.google.common.base.Preconditions;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ReconnectPromise<S extends ProtocolSession<?>, L extends SessionListener<?, ?, ?>> extends DefaultPromise<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(ReconnectPromise.class);

    private final AbstractDispatcher<S, L> dispatcher;
    private final InetSocketAddress address;
    private final ReconnectStrategyFactory strategyFactory;
    private final Bootstrap b;
    private final AbstractDispatcher.PipelineInitializer<S> initializer;
    private Future<?> pending;

    public ReconnectPromise(final EventExecutor executor, final AbstractDispatcher<S, L> dispatcher, final InetSocketAddress address,
                            final ReconnectStrategyFactory connectStrategyFactory, final Bootstrap b, final AbstractDispatcher.PipelineInitializer<S> initializer) {
        super(executor);
        this.b = b;
        this.initializer = Preconditions.checkNotNull(initializer);
        this.dispatcher = Preconditions.checkNotNull(dispatcher);
        this.address = Preconditions.checkNotNull(address);
        this.strategyFactory = Preconditions.checkNotNull(connectStrategyFactory);
    }

    synchronized void connect() {
        final ReconnectStrategy cs = this.strategyFactory.createReconnectStrategy();

        // Set up a client with pre-configured bootstrap, but add a closed channel handler into the pipeline to support reconnect attempts
        pending = this.dispatcher.createClient(this.address, cs, b, new AbstractDispatcher.PipelineInitializer<S>() {
            @Override
            public void initializeChannel(final SocketChannel channel, final Promise<S> promise) {
                // add closed channel handler
                // This handler has to be added before initializer.initializeChannel is called
                // Initializer might add some handlers using addFirst e.g. AsyncSshHandler and in that case
                // closed channel handler is before the handler that invokes channel inactive event
                channel.pipeline().addFirst(new ClosedChannelHandler(ReconnectPromise.this));

                initializer.initializeChannel(channel, promise);
            }
        });
    }

    /**
     *
     * @return true if initial connection was established successfully, false if initial connection failed due to e.g. Connection refused, Negotiation failed
     */
    private boolean isInitialConnectFinished() {
        Preconditions.checkNotNull(pending);
        return pending.isDone() && pending.isSuccess();
    }

    @Override
    public synchronized boolean cancel(final boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            Preconditions.checkNotNull(pending);
            this.pending.cancel(mayInterruptIfRunning);
            return true;
        }

        return false;
    }

    /**
     * Channel handler that responds to channelInactive event and reconnects the session.
     * Only if the initial connection was successfully established and promise was not canceled.
     */
    private static final class ClosedChannelHandler extends ChannelInboundHandlerAdapter {
        private final ReconnectPromise<?, ?> promise;

        public ClosedChannelHandler(final ReconnectPromise<?, ?> promise) {
            this.promise = promise;
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
            // Pass info about disconnect further and then reconnect
            super.channelInactive(ctx);

            if (promise.isCancelled()) {
                return;
            }

            // Check if initial connection was fully finished. If the session was dropped during negotiation, reconnect will not happen.
            // Session can be dropped during negotiation on purpose by the client side and would make no sense to initiate reconnect
            if (promise.isInitialConnectFinished() == false) {
                return;
            }

            LOG.debug("Reconnecting after connection to {} was dropped", promise.address);
            promise.connect();
        }
    }

}
