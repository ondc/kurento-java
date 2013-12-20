package com.kurento.kmf.jsonrpcconnector.internal.server;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;

import com.google.gson.JsonElement;
import com.kurento.kmf.jsonrpcconnector.internal.JsonRpcRequestSenderHelper;
import com.kurento.kmf.jsonrpcconnector.internal.message.Response;

public abstract class ServerSession extends AbstractSession {

	private SessionsManager sessionsManager;
	private JsonRpcRequestSenderHelper rsHelper;
	private String transportId;
	private ScheduledFuture<?> closeTimerTask;

	// TODO Make this configurable
	private long reconnectionTimeoutInMillis = 10000;

	public ServerSession(String sessionId, Object registerInfo,
			SessionsManager sessionsManager, String transportId) {

		super(sessionId, registerInfo);

		this.transportId = transportId;
		this.sessionsManager = sessionsManager;
	}

	public abstract void handleResponse(Response<JsonElement> response);

	public String getTransportId() {
		return transportId;
	}

	public void setTransportId(String transportId) {
		this.transportId = transportId;
	}

	public void close() throws IOException {
		this.sessionsManager.remove(this.getSessionId());
	}

	protected void setRsHelper(JsonRpcRequestSenderHelper rsHelper) {
		this.rsHelper = rsHelper;
	}

	@Override
	public <R> R sendRequest(String method, Class<R> resultClass)
			throws IOException {
		return rsHelper.sendRequest(method, resultClass);
	}

	@Override
	public <R> R sendRequest(String method, Object params, Class<R> resultClass)
			throws IOException {
		return rsHelper.sendRequest(method, params, resultClass);
	}

	@Override
	public JsonElement sendRequest(String method) throws IOException {
		return rsHelper.sendRequest(method);
	}

	@Override
	public JsonElement sendRequest(String method, Object params)
			throws IOException {
		return rsHelper.sendRequest(method, params);
	}

	@Override
	public void sendNotification(String method, Object params)
			throws IOException {
		rsHelper.sendNotification(method, params);
	}

	@Override
	public void sendNotification(String method) throws IOException {
		rsHelper.sendNotification(method);
	}

	public void setCloseTimerTask(ScheduledFuture<?> closeTimerTask) {
		this.closeTimerTask = closeTimerTask;
	}

	public ScheduledFuture<?> getCloseTimerTask() {
		return closeTimerTask;
	}

	@Override
	public void setReconnectionTimeout(long reconnectionTimeoutInMillis) {
		this.reconnectionTimeoutInMillis = reconnectionTimeoutInMillis;
	}

	public long getReconnectionTimeoutInMillis() {
		return reconnectionTimeoutInMillis;
	}
}
