package com.kurento.kmf.jsonrpcconnector.internal.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.util.Assert;
import org.springframework.web.socket.handler.PerConnectionWebSocketHandler;

import com.kurento.kmf.jsonrpcconnector.JsonRpcHandler;
import com.kurento.kmf.jsonrpcconnector.Session;
import com.kurento.kmf.jsonrpcconnector.Transaction;
import com.kurento.kmf.jsonrpcconnector.internal.message.Request;

public class PerSessionJsonRpcHandler<T> implements JsonRpcHandler<T>,
		BeanFactoryAware {

	private static final Log logger = LogFactory
			.getLog(PerConnectionWebSocketHandler.class);

	private final BeanCreatingHelper<JsonRpcHandler<T>> provider;

	private final Map<Session, JsonRpcHandler<T>> handlers = new ConcurrentHashMap<Session, JsonRpcHandler<T>>();

	public PerSessionJsonRpcHandler(String handlerName) {
		this(handlerName, null);
	}

	public PerSessionJsonRpcHandler(
			Class<? extends JsonRpcHandler<T>> handlerType) {
		this(null, handlerType);
	}

	public PerSessionJsonRpcHandler(String handlerName,
			Class<? extends JsonRpcHandler<T>> handlerType) {
		this.provider = new BeanCreatingHelper<JsonRpcHandler<T>>(handlerType,
				handlerName);
	}

	@SuppressWarnings("unchecked")
	public Class<? extends JsonRpcHandler<T>> getHandlerType() {
		return (Class<? extends JsonRpcHandler<T>>) provider
				.getCreatedBeanType();
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.provider.setBeanFactory(beanFactory);
	}

	@Override
	public void handleRequest(Transaction transaction, Request<T> request)
			throws Exception {

		JsonRpcHandler<T> handler = getHandler(transaction.getSession());
		try {
			handler.handleRequest(transaction, request);
		} catch (Exception e) {
			handler.handleUncaughtException(transaction.getSession(), e);
		}
	}

	private JsonRpcHandler<T> getHandler(Session session) {
		JsonRpcHandler<T> handler = this.handlers.get(session);
		Assert.isTrue(handler != null, "JsonRpcHandler not found for "
				+ session);
		return handler;
	}

	@Override
	public void afterConnectionEstablished(Session session) throws Exception {
		JsonRpcHandler<T> handler = this.provider.createBean();
		this.handlers.put(session, handler);

		try {
			handler.afterConnectionEstablished(session);
		} catch (Exception e) {
			handler.handleUncaughtException(session, e);
		}
	}

	@Override
	public void afterConnectionClosed(Session session, String status)
			throws Exception {
		try {
			JsonRpcHandler<T> handler = getHandler(session);
			try {
				handler.afterConnectionClosed(session, status);
			} catch (Exception e) {
				handler.handleUncaughtException(session, e);
			}
		} finally {
			destroy(session);
		}
	}

	private void destroy(Session session) {
		JsonRpcHandler<T> handler = this.handlers.remove(session);
		try {
			if (handler != null) {
				this.provider.destroy(handler);
			}
		} catch (Throwable t) {
			logger.warn("Error while destroying handler", t);
		}
	}

	@Override
	public void handleTransportError(Session session, Throwable exception)
			throws Exception {
		JsonRpcHandler<T> handler = getHandler(session);
		try {
			handler.handleTransportError(session, exception);
		} catch (Exception e) {
			handler.handleUncaughtException(session, e);
		}
	}

	@Override
	public void handleUncaughtException(Session session, Exception exception) {
		logger.error(
				"Uncaught exception while execution PerSessionJsonRpcHandler",
				exception);
	}

}
