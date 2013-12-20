package com.kurento.kmf.jsonrpcconnector.client;

import static com.kurento.kmf.jsonrpcconnector.JsonUtils.fromJson;
import static com.kurento.kmf.jsonrpcconnector.JsonUtils.fromJsonRequest;
import static com.kurento.kmf.jsonrpcconnector.JsonUtils.fromJsonResponse;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kurento.kmf.jsonrpcconnector.internal.JsonRpcConstants;
import com.kurento.kmf.jsonrpcconnector.internal.JsonRpcRequestSenderHelper;
import com.kurento.kmf.jsonrpcconnector.internal.client.ClientSession;
import com.kurento.kmf.jsonrpcconnector.internal.message.MessageUtils;
import com.kurento.kmf.jsonrpcconnector.internal.message.Request;
import com.kurento.kmf.jsonrpcconnector.internal.message.Response;
import com.kurento.kmf.jsonrpcconnector.internal.server.TransactionImpl.ResponseSender;
import com.kurento.kmf.jsonrpcconnector.internal.ws.PendingRequests;
import com.kurento.kmf.jsonrpcconnector.internal.ws.WebSocketResponseSender;

public class JsonRpcClientWebSocket extends JsonRpcClient {

	private Logger log = LoggerFactory.getLogger(JsonRpcClient.class);

	private String url;
	private volatile WebSocketSession wsSession;
	private PendingRequests pendingRequests = new PendingRequests();

	private ResponseSender rs;

	public JsonRpcClientWebSocket(String url) {

		// Append /ws to avoid collisions with http
		// Append /websockets to point to websocket interface in SockJS
		this.url = url + "/ws/websocket";

		rsHelper = new JsonRpcRequestSenderHelper() {
			@Override
			public <P, R> Response<R> internalSendRequest(Request<P> request,
					Class<R> resultClass) throws IOException {

				return sendRequestForHelper(request, resultClass);
			}
		};
	}

	private synchronized void connectIfNecessary() throws IOException {

		if (wsSession == null) {

			final CountDownLatch latch = new CountDownLatch(1);

			TextWebSocketHandler webSocketHandler = new TextWebSocketHandler() {

				@Override
				public void afterConnectionEstablished(
						WebSocketSession wsSession2) throws Exception {

					wsSession = wsSession2;
					rs = new WebSocketResponseSender(wsSession);
					latch.countDown();
				}

				@Override
				public void handleTextMessage(WebSocketSession session,
						TextMessage message) throws Exception {
					handleWebSocketTextMessage(message);
				}

				@Override
				public void afterConnectionClosed(WebSocketSession s,
						CloseStatus status) throws Exception {

					// TODO Call this when you can't reconnect or close is
					// issued by client.
					handlerManager.afterConnectionClosed(session,
							status.getReason());
					log.debug("WebSocket closed due to: " + status);
					wsSession = null;
					// TODO Start a timer to force reconnect in x millis
					// For the moment we are going to force it sending another
					// message.
				}
			};

			WebSocketConnectionManager connectionManager = new WebSocketConnectionManager(
					new StandardWebSocketClient(), webSocketHandler, url);

			connectionManager.start();

			try {
				latch.await();

				if (session == null) {

					session = new ClientSession(null, null,
							JsonRpcClientWebSocket.this);
					handlerManager.afterConnectionEstablished(session);

				} else {

					String result = rsHelper.sendRequest(
							JsonRpcConstants.METHOD_RECONNECT, String.class);

					log.info("Reconnection result: " + result);

				}

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void handleWebSocketTextMessage(TextMessage message)
			throws IOException {

		JsonObject jsonMessage = fromJson(message.getPayload(),
				JsonObject.class);

		if (jsonMessage.has(JsonRpcConstants.METHOD_PROPERTY)) {
			handleRequestFromServer(jsonMessage);
		} else {
			handleResponseFromServer(jsonMessage);
		}
	}

	private void handleRequestFromServer(JsonObject message) throws IOException {
		log.info("[Client] Message Received: " + message);
		handlerManager.handleRequest(session,
				fromJsonRequest(message, JsonElement.class), rs);
	}

	private void handleResponseFromServer(JsonObject message) {
		log.info("[Client] Message Received: " + message);

		Response<JsonElement> response = fromJsonResponse(message,
				JsonElement.class);

		updateSessionId(response.getSessionId());

		pendingRequests.handleResponse(response);
	}

	private void updateSessionId(String sessionId) {
		this.session.setSessionId(sessionId);
		this.rsHelper.setSessionId(sessionId);
	}

	private <P, R> Response<R> sendRequestForHelper(Request<P> request,
			Class<R> resultClass) throws IOException {

		connectIfNecessary();

		Future<Response<JsonElement>> responseFuture = null;

		if (request.getId() != null) {
			responseFuture = pendingRequests.prepareResponse(request.getId());
		}

		String jsonMessage = request.toString();
		log.info("[Client] Message sent: " + jsonMessage);
		wsSession.sendMessage(new TextMessage(jsonMessage));

		if (responseFuture == null) {
			return null;
		}

		try {

			Response<R> response = MessageUtils.convertResponse(
					responseFuture.get(), resultClass);

			if (response.getSessionId() != null) {
				session.setSessionId(response.getSessionId());
			}

			return response;

		} catch (InterruptedException e) {
			// TODO What to do in this case?
			throw new RuntimeException(
					"Interrupted while waiting for a response", e);
		} catch (ExecutionException e) {
			// TODO Is there a better way to handle this?
			throw new RuntimeException("This exception shouldn't be thrown", e);
		}
	}

	@Override
	public void close() throws IOException {
		if (wsSession != null) {
			wsSession.close();
		}
	}

	public WebSocketSession getWebSocketSession() {
		return wsSession;
	}

}
