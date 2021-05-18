package name.qd.tp2.exchanges.BTSE;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import name.qd.tp2.exchanges.AbstractExchange;
import name.qd.tp2.exchanges.ChannelMessageHandler;
import name.qd.tp2.exchanges.Exchange;
import name.qd.tp2.exchanges.vo.Orderbook;
import name.qd.tp2.utils.JsonUtils;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class BTSEExchange extends AbstractExchange {
	private Logger log = LoggerFactory.getLogger(BTSEExchange.class);
	private WebSocket webSocket;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private ChannelMessageHandler channelMessageHandler = new BTSEChannelMessageHandler();
	private boolean isWebSocketConnect = false;
	
	public BTSEExchange() {
		executor.execute(channelMessageHandler);
		if(webSocket == null) initWebSocket();
	}
	
	@Override
	public boolean isReady() {
		// 這個交易所websocket連上就算ready
		return isWebSocketConnect;
	}
	
	@Override
	public void subscribe(String symbol) {
		ObjectNode objectNode = JsonUtils.objectMapper.createObjectNode();
		objectNode.put("op", "subscribe");
		ArrayNode arrayNode = objectNode.putArray("args");
		arrayNode.add("orderBook:" + symbol + "_0");
		webSocket.send(objectNode.toString());
	}

	@Override
	public void unsubscribe(String symbol) {
		ObjectNode objectNode = JsonUtils.objectMapper.createObjectNode();
		objectNode.put("op", "unsubscribe");
		ArrayNode arrayNode = objectNode.putArray("args");
		arrayNode.add("orderBook:" + symbol + "_0");
		webSocket.send(objectNode.toString());
	}

	@Override
	public String sendOrder(String userName, String strategyName, String symbol, double price, double qty) {
		return null;
	}

	@Override
	public boolean cancelOrder(String userName, String strategyName, String orderId) {
		return false;
	}

	@Override
	public String getBalance(String userName, String symbol) {
		return null;
	}
	
	private void initWebSocket() {
		webSocket = createWebSocket("wss://ws.btse.com/ws/futures", new BTSEWebSocketListener());
	}
	
	private void processOrderbook(JsonNode node) {
		String symbol = node.get("data").get("symbol").asText();
		Orderbook orderbook = new Orderbook();
		JsonNode buyNode = node.get("data").get("buyQuote");
		JsonNode sellNode = node.get("data").get("sellQuote");
		for(JsonNode buy : buyNode) {
			orderbook.addBid(buy.get("price").asDouble(), buy.get("size").asDouble());
		}
		for(JsonNode sell : sellNode) {
			orderbook.addAsk(sell.get("price").asDouble(), sell.get("size").asDouble());
		}
		updateOrderbook(symbol, orderbook);
	}
	
	private class BTSEWebSocketListener extends WebSocketListener {
		@Override
		public void onOpen(WebSocket socket, Response response) {
			isWebSocketConnect = true;
			log.info("BTSE websocket opened.");
		}
		
		@Override
		public void onMessage(WebSocket webSocket, String text) {
			channelMessageHandler.onMessage(text);
		}
		
		@Override
		public void onClosed(WebSocket webSocket, int code, String reason) {
			isWebSocketConnect = false;
			log.info("BTSE websocket closed. {}", reason);
		}
		
		@Override
		public void onFailure(WebSocket webSocket, Throwable t, Response response) {
			isWebSocketConnect = false;
			log.error("BTSE websocket failure.", t);
		}
	}
	
	private class BTSEChannelMessageHandler extends ChannelMessageHandler {
		@Override
		public void processMessage(String text) {
			try {
				JsonNode node = JsonUtils.objectMapper.readTree(text);
				if(node.has("topic")) {
					String messageType = node.get("topic").asText();
					if("orderBook".equals(messageType)) {
						processOrderbook(node);
					}
				}
			} catch (JsonProcessingException e) {
				log.error("Parse websocket message to Json format failed. {}", text, e);
			}
		}
	}
	
	public static void main(String[] s) {
		Exchange exchange = new BTSEExchange();
		exchange.subscribe("ETHPFC");
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		exchange.unsubscribe("ETHPFC");
	}
}