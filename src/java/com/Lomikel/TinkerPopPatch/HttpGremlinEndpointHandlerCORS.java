/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.Lomikel.TinkerPopPatch;
import org.apache.tinkerpop.gremlin.server.handler.*;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import org.javatuples.Pair;
import org.javatuples.Quartet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.tinkerpop.gremlin.driver.MessageSerializer;
import org.apache.tinkerpop.gremlin.driver.Tokens;
import org.apache.tinkerpop.gremlin.driver.message.ResponseMessage;
import org.apache.tinkerpop.gremlin.driver.message.ResponseStatusCode;
import org.apache.tinkerpop.gremlin.driver.ser.MessageTextSerializer;
import org.apache.tinkerpop.gremlin.groovy.engine.GremlinExecutor;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalSource;
import org.apache.tinkerpop.gremlin.server.GraphManager;
import org.apache.tinkerpop.gremlin.server.GremlinServer;
import org.apache.tinkerpop.gremlin.server.Settings;
import org.apache.tinkerpop.gremlin.server.util.MetricManager;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.util.function.FunctionUtils;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import org.apache.tinkerpop.shaded.jackson.databind.JsonNode;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;
import org.apache.tinkerpop.shaded.jackson.databind.node.ArrayNode;
import org.apache.tinkerpop.shaded.jackson.databind.node.ObjectNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import javax.script.Bindings;
import javax.script.SimpleBindings;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.codahale.metrics.MetricRegistry.name;
import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Handler that processes HTTP requests to the HTTP Gremlin endpoint.
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
@ChannelHandler.Sharable
public class HttpGremlinEndpointHandlerCORS extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(HttpGremlinEndpointHandlerCORS.class);
    private static final Logger auditLogger = LoggerFactory.getLogger(GremlinServer.AUDIT_LOGGER_NAME);
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    static final Meter errorMeter = MetricManager.INSTANCE.getMeter(name(GremlinServer.class, "errors"));

    private static final String ARGS_BINDINGS_DOT = Tokens.ARGS_BINDINGS + ".";

    private static final String ARGS_ALIASES_DOT = Tokens.ARGS_ALIASES + ".";

    private static final Timer evalOpTimer = MetricManager.INSTANCE.getTimer(name(GremlinServer.class, "op", "eval"));

    /**
     * Serializers for the response.
     */
    private final Map<String, MessageSerializer> serializers;

    /**
     * This is just a generic mapper to interpret the JSON of a POSTed request.  It is not used for the serialization
     * of the response.
     */
    private static final ObjectMapper mapper = new ObjectMapper();

    private final GremlinExecutor gremlinExecutor;
    private final GraphManager graphManager;
    private final Settings settings;

    private static final Pattern pattern = Pattern.compile("(.*);q=(.*)");

    public HttpGremlinEndpointHandlerCORS(final Map<String, MessageSerializer<?>> serializers,
                                          final GremlinExecutor gremlinExecutor,
                                          final GraphManager graphManager,
                                          final Settings settings) {
        this.serializers = serializers;
        this.gremlinExecutor = gremlinExecutor;
        this.graphManager = graphManager;
        this.settings = settings;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof FullHttpRequest) {
            final FullHttpRequest req = (FullHttpRequest) msg;
            final boolean keepAlive = HttpUtil.isKeepAlive(req);

            if ("/favicon.ico".equals(req.uri())) {
                sendError(ctx, NOT_FOUND, "Gremlin Server doesn't have a favicon.ico", keepAlive);
                ReferenceCountUtil.release(msg);
                return;
            }

            if (HttpUtil.is100ContinueExpected(req)) {
                ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
            }

            if (req.method() != GET && req.method() != POST) {
                sendError(ctx, METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED.toString(), keepAlive);
                ReferenceCountUtil.release(msg);
                return;
            }

            final Quartet<String, Map<String, Object>, String, Map<String, String>> requestArguments;
            try {
                requestArguments = getRequestArguments(req);
            } catch (IllegalArgumentException iae) {
                sendError(ctx, BAD_REQUEST, iae.getMessage(), keepAlive);
                ReferenceCountUtil.release(msg);
                return;
            }

            final String acceptString = Optional.ofNullable(req.headers().get("Accept")).orElse("application/json");
            final Pair<String, MessageTextSerializer> serializer = chooseSerializer(acceptString);
            if (null == serializer) {
                sendError(ctx, BAD_REQUEST, String.format("no serializer for requested Accept header: %s", acceptString),
                        keepAlive);
                ReferenceCountUtil.release(msg);
                return;
            }

            final String origin = req.headers().get(ORIGIN);

            // not using the req any where below here - assume it is safe to release at this point.
            ReferenceCountUtil.release(msg);

            try {
                logger.debug("Processing request containing script [{}] and bindings of [{}] on {}",
                        requestArguments.getValue0(), requestArguments.getValue1(), Thread.currentThread().getName());
                if (settings.authentication.enableAuditLog) {
                    String address = ctx.channel().remoteAddress().toString();
                    if (address.startsWith("/") && address.length() > 1) address = address.substring(1);
                    auditLogger.info("User with address {} requested: {}", address, requestArguments.getValue0());
                }
                final ChannelPromise promise = ctx.channel().newPromise();
                final AtomicReference<Object> resultHolder = new AtomicReference<>();
                promise.addListener(future -> {
                    // if failed then the error was already written back to the client as part of the eval future
                    // processing of the exception
                    if (future.isSuccess()) {
                        logger.debug("Preparing HTTP response for request with script [{}] and bindings of [{}] with result of [{}] on [{}]",
                                requestArguments.getValue0(), requestArguments.getValue1(), resultHolder.get(), Thread.currentThread().getName());
                        final FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, (ByteBuf) resultHolder.get());
                        response.headers().set(CONTENT_TYPE, serializer.getValue0());

                        // handle cors business
                        if (origin != null) {
                          //response.headers().set(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                          response.headers().set(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                          response.headers().add(ACCESS_CONTROL_ALLOW_METHODS, "GET, POST");
                          response.headers().add(ACCESS_CONTROL_ALLOW_HEADERS, "origin, content-type, accept, X-Requested-With, authorization");
                          response.headers().add(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
                          }
                        sendAndCleanupConnection(ctx, keepAlive, response);
                    }
                });

                final Timer.Context timerContext = evalOpTimer.time();

                final Bindings bindings;
                try {
                    bindings = createBindings(requestArguments.getValue1(), requestArguments.getValue3());
                } catch (IllegalStateException iae) {
                    sendError(ctx, BAD_REQUEST, iae.getMessage(), keepAlive);
                    ReferenceCountUtil.release(msg);
                    return;
                }

                // provide a transform function to serialize to message - this will force serialization to occur
                // in the same thread as the eval. after the CompletableFuture is returned from the eval the result
                // is ready to be written as a ByteBuf directly to the response.  nothing should be blocking here.
                final CompletableFuture<Object> evalFuture = gremlinExecutor.eval(requestArguments.getValue0(), requestArguments.getValue2(), bindings,
                        FunctionUtils.wrapFunction(o -> {
                            // stopping the timer here is roughly equivalent to where the timer would have been stopped for
                            // this metric in other contexts.  we just want to measure eval time not serialization time.
                            timerContext.stop();

                            logger.debug("Transforming result of request with script [{}] and bindings of [{}] with result of [{}] on [{}]",
                                    requestArguments.getValue0(), requestArguments.getValue1(), o, Thread.currentThread().getName());
                            final ResponseMessage responseMessage = ResponseMessage.build(UUID.randomUUID())
                                    .code(ResponseStatusCode.SUCCESS)
                                    .result(IteratorUtils.asList(o)).create();

                            // http server is sessionless and must handle commit on transactions. the commit occurs
                            // before serialization to be consistent with how things work for websocket based
                            // communication.  this means that failed serialization does not mean that you won't get
                            // a commit to the database
                            attemptCommit(requestArguments.getValue3(), graphManager, settings.strictTransactionManagement);

                            try {
                                return Unpooled.wrappedBuffer(serializer.getValue1().serializeResponseAsString(responseMessage).getBytes(UTF8));
                            } catch (Exception ex) {
                                logger.warn(String.format("Error during serialization for %s", responseMessage), ex);
                                throw ex;
                            }
                        }));

                evalFuture.exceptionally(t -> {
                    if (t.getMessage() != null)
                        sendError(ctx, INTERNAL_SERVER_ERROR, t.getMessage(), Optional.of(t), keepAlive);
                    else
                        sendError(ctx, INTERNAL_SERVER_ERROR, String.format("Error encountered evaluating script: %s", requestArguments.getValue0())
                                , Optional.of(t), keepAlive);
                    promise.setFailure(t);
                    return null;
                });

                evalFuture.thenAcceptAsync(r -> {
                    // now that the eval/serialization is done in the same thread - complete the promise so we can
                    // write back the HTTP response on the same thread as the original request
                    resultHolder.set(r);
                    promise.setSuccess();
                }, gremlinExecutor.getExecutorService());
            } catch (Exception ex) {
                // send the error response here and don't rely on exception caught because it might not have the
                // context on whether to close the connection or not, based on keepalive.
                final Throwable t = ExceptionUtils.getRootCause(ex);
                if (t instanceof TooLongFrameException) {
                    sendError(ctx, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, t.getMessage() + " - increase the maxContentLength", keepAlive);
                } else if (t != null){
                    sendError(ctx, INTERNAL_SERVER_ERROR, t.getMessage(), keepAlive);
                } else {
                    sendError(ctx, INTERNAL_SERVER_ERROR, ex.getMessage(), keepAlive);
                }
            }
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        logger.error("Error processing HTTP Request", cause);

        if (ctx.channel().isActive()) {
            sendError(ctx, INTERNAL_SERVER_ERROR, cause.getMessage(), false);
        }
    }

    private Bindings createBindings(final Map<String, Object> bindingMap, final Map<String, String> rebindingMap) {
        final Bindings bindings = new SimpleBindings();

        // rebind any global bindings to a different variable.
        if (!rebindingMap.isEmpty()) {
            for (Map.Entry<String, String> kv : rebindingMap.entrySet()) {
                boolean found = false;
                final Graph g = this.graphManager.getGraph(kv.getValue());
                if (null != g) {
                    bindings.put(kv.getKey(), g);
                    found = true;
                }

                if (!found) {
                    final TraversalSource ts = this.graphManager.getTraversalSource(kv.getValue());
                    if (null != ts) {
                        bindings.put(kv.getKey(), ts);
                        found = true;
                    }
                }

                if (!found) {
                    final String error = String.format("Could not rebind [%s] to [%s] as [%s] not in the Graph or TraversalSource global bindings",
                            kv.getKey(), kv.getValue(), kv.getValue());
                    throw new IllegalStateException(error);
                }
            }
        }

        bindings.putAll(bindingMap);

        return bindings;
    }

    private Pair<String, MessageTextSerializer> chooseSerializer(final String acceptString) {
        final List<Pair<String, Double>> ordered = Stream.of(acceptString.split(",")).map(mediaType -> {
            // parse out each mediaType with its params - keeping it simple and just looking for "quality".  if
            // that value isn't there, default it to 1.0.  not really validating here so users better get their
            // accept headers straight
            final Matcher matcher = pattern.matcher(mediaType);
            return (matcher.matches()) ? Pair.with(matcher.group(1), Double.parseDouble(matcher.group(2))) : Pair.with(mediaType, 1.0);
        }).sorted((o1, o2) -> o2.getValue0().compareTo(o1.getValue0())).collect(Collectors.toList());

        for (Pair<String, Double> p : ordered) {
            // this isn't perfect as it doesn't really account for wildcards.  that level of complexity doesn't seem
            // super useful for gremlin server really.
            final String accept = p.getValue0().equals("*/*") ? "application/json" : p.getValue0();
            if (serializers.containsKey(accept))
                return Pair.with(accept, (MessageTextSerializer) serializers.get(accept));
        }

        return null;
    }

    private static Quartet<String, Map<String, Object>, String, Map<String, String>> getRequestArguments(final FullHttpRequest request) {
        if (request.method() == GET) {
            final QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            final List<String> gremlinParms = decoder.parameters().get(Tokens.ARGS_GREMLIN);

            if (null == gremlinParms || gremlinParms.size() == 0)
                throw new IllegalArgumentException("no gremlin script supplied");
            final String script = gremlinParms.get(0);
            if (script.isEmpty()) throw new IllegalArgumentException("no gremlin script supplied");

            // query string parameters - take the first instance of a key only - ignore the rest
            final Map<String, Object> bindings = new HashMap<>();
            decoder.parameters().entrySet().stream().filter(kv -> kv.getKey().startsWith(ARGS_BINDINGS_DOT))
                    .forEach(kv -> bindings.put(kv.getKey().substring(ARGS_BINDINGS_DOT.length()), kv.getValue().get(0)));

            final Map<String, String> aliases = new HashMap<>();
            decoder.parameters().entrySet().stream().filter(kv -> kv.getKey().startsWith(ARGS_ALIASES_DOT))
                    .forEach(kv -> aliases.put(kv.getKey().substring(ARGS_ALIASES_DOT.length()), kv.getValue().get(0)));

            final List<String> languageParms = decoder.parameters().get(Tokens.ARGS_LANGUAGE);
            final String language = (null == languageParms || languageParms.size() == 0) ? null : languageParms.get(0);

            return Quartet.with(script, bindings, language, aliases);
        } else {
            final JsonNode body;
            try {
                body = mapper.readTree(request.content().toString(CharsetUtil.UTF_8));
            } catch (IOException ioe) {
                throw new IllegalArgumentException("body could not be parsed", ioe);
            }

            final JsonNode scriptNode = body.get(Tokens.ARGS_GREMLIN);
            if (null == scriptNode) throw new IllegalArgumentException("no gremlin script supplied");

            final JsonNode bindingsNode = body.get(Tokens.ARGS_BINDINGS);
            if (bindingsNode != null && !bindingsNode.isObject())
                throw new IllegalArgumentException("bindings must be a Map");

            final Map<String, Object> bindings = new HashMap<>();
            if (bindingsNode != null)
                bindingsNode.fields().forEachRemaining(kv -> bindings.put(kv.getKey(), fromJsonNode(kv.getValue())));

            final JsonNode aliasesNode = body.get(Tokens.ARGS_ALIASES);
            if (aliasesNode != null && !aliasesNode.isObject())
                throw new IllegalArgumentException("aliases must be a Map");

            final Map<String, String> aliases = new HashMap<>();
            if (aliasesNode != null)
                aliasesNode.fields().forEachRemaining(kv -> aliases.put(kv.getKey(), kv.getValue().asText()));

            final JsonNode languageNode = body.get(Tokens.ARGS_LANGUAGE);
            final String language = null == languageNode ? null : languageNode.asText();

            return Quartet.with(scriptNode.asText(), bindings, language, aliases);
        }
    }

    public static Object fromJsonNode(final JsonNode node) {
        if (node.isNull())
            return null;
        else if (node.isObject()) {
            final Map<String, Object> map = new HashMap<>();
            final ObjectNode objectNode = (ObjectNode) node;
            final Iterator<String> iterator = objectNode.fieldNames();
            while (iterator.hasNext()) {
                String key = iterator.next();
                map.put(key, fromJsonNode(objectNode.get(key)));
            }
            return map;
        } else if (node.isArray()) {
            final ArrayNode arrayNode = (ArrayNode) node;
            final ArrayList<Object> array = new ArrayList<>();
            for (int i = 0; i < arrayNode.size(); i++) {
                array.add(fromJsonNode(arrayNode.get(i)));
            }
            return array;
        } else if (node.isFloatingPointNumber())
            return node.asDouble();
        else if (node.isIntegralNumber())
            return node.asLong();
        else if (node.isBoolean())
            return node.asBoolean();
        else
            return node.asText();
    }

    private static void sendError(final ChannelHandlerContext ctx, final HttpResponseStatus status,
                                  final String message, final boolean keepAlive) {
        sendError(ctx, status, message, Optional.empty(), keepAlive);
    }

    private static void sendError(final ChannelHandlerContext ctx, final HttpResponseStatus status,
                                  final String message, final Optional<Throwable> t, final boolean keepAlive) {
        if (t.isPresent())
            logger.warn(String.format("Invalid request - responding with %s and %s", status, message), t.get());
        else
            logger.warn(String.format("Invalid request - responding with %s and %s", status, message));

        errorMeter.mark();
        final ObjectNode node = mapper.createObjectNode();
        node.put("message", message);
        if (t.isPresent()) {
            // "Exception-Class" needs to go away - didn't realize it was named that way during review for some reason.
            // replaced with the same method for exception reporting as is used with websocket/nio protocol
            node.put("Exception-Class", t.get().getClass().getName());
            final ArrayNode exceptionList = node.putArray(Tokens.STATUS_ATTRIBUTE_EXCEPTIONS);
            ExceptionUtils.getThrowableList(t.get()).forEach(throwable -> exceptionList.add(throwable.getClass().getName()));
            node.put(Tokens.STATUS_ATTRIBUTE_STACK_TRACE, ExceptionUtils.getStackTrace(t.get()));
        }

        final FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, Unpooled.copiedBuffer(node.toString(), CharsetUtil.UTF_8));
        response.headers().set(CONTENT_TYPE, "application/json");

        sendAndCleanupConnection(ctx, keepAlive, response);
    }

    private static void sendAndCleanupConnection(final ChannelHandlerContext ctx,
                                                 final boolean keepAlive,
                                                 final FullHttpResponse response) {
        HttpUtil.setKeepAlive(response, keepAlive);
        HttpUtil.setContentLength(response, response.content().readableBytes());

        final ChannelFuture flushPromise = ctx.writeAndFlush(response);

        if (!keepAlive) {
            // Close the connection as soon as the response is sent.
            flushPromise.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static void attemptCommit(final Map<String, String> aliases, final GraphManager graphManager, final boolean strict) {
        if (strict)
            graphManager.commit(new HashSet<>(aliases.values()));
        else
            graphManager.commitAll();
    }
}
