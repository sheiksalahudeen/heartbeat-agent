package com.avanza.heartbeat.agent;

import static java.util.stream.Collectors.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.avanza.heartbeat.agent.util.WebContainerRule;
import com.avanza.heartbeat.agent.util.WebRequestHandler;

public class HearbeatComponentTest {
    
    private FakeHBServer hbServer = new FakeHBServer();
    
    @Rule
    public WebContainerRule webRule = new WebContainerRule(hbServer);

	private HeartbeatClient client;

	@After
	public void stopClient() {
		if (client != null) {
			client.stop();
		}
	}
	
    @Test
    public void allProperties() throws Exception {
    	HeartbeatProperties props = new HeartbeatProperties(new URL("http://localhost:" + webRule.getPort() + "/beat"), "my-app", 123, "1.0.0", 11223);
    	client = new HeartbeatClient(props, HeartbeatClientId.fromString("abc123123abc"), 5000);
    	client.start();
    	Map<String, String> expected = new HashMap<>();
    	expected.put("name", "my-app");
    	expected.put("pid", "123");
    	expected.put("uid", "abc123123abc");
    	expected.put("revision", "1.0.0");
    	expected.put("jmx", "11223");
    	eventually(() -> assertThat(hbServer.getHandledRequests(), hasItem(expected)), 1000);
    }

    @Test
    public void optionalJmxPort() throws Exception {
    	HeartbeatProperties props = new HeartbeatProperties(new URL("http://localhost:" + webRule.getPort() + "/beat"), "my-app", 123, "1.0.0");
    	client = new HeartbeatClient(props, HeartbeatClientId.fromString("abc123123abc"), 5000);
    	client.start();
    	Map<String, String> expected = new HashMap<>();
    	expected.put("name", "my-app");
    	expected.put("pid", "123");
    	expected.put("uid", "abc123123abc");
    	expected.put("revision", "1.0.0");
    	eventually(() -> assertThat(hbServer.getHandledRequests(), hasItem(expected)), 1000);
    }

    @SuppressWarnings("unchecked")
	@Test
    public void moreThanOneHeartbeatIsMade() throws Exception {
    	HeartbeatProperties props = new HeartbeatProperties(new URL("http://localhost:" + webRule.getPort() + "/beat"), "my-app", 123, "1.0.0", 11223);
    	client = new HeartbeatClient(props, HeartbeatClientId.fromString("abc123123abc"), 200);
    	client.start();
    	Map<String, String> expected = new HashMap<>();
    	expected.put("name", "my-app");
    	expected.put("pid", "123");
    	expected.put("uid", "abc123123abc");
    	expected.put("revision", "1.0.0");
    	expected.put("jmx", "11223");
    	eventually(() -> assertThat(hbServer.getHandledRequests(), contains(expected, expected)), 2000);
    }
    
	private void eventually(Runnable test, long durationMillis) {
		long start = System.nanoTime();
		AssertionError lastError = null;
		while (System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(durationMillis)) {
			try {
				test.run();
				return;
			}  catch (AssertionError error) {
				lastError = error;
			}
			sleep(50);
		}
		throw lastError;
	}

	private void sleep(int sleepTime) {
		try {
			Thread.sleep(sleepTime);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}


	private static class FakeHBServer implements WebRequestHandler {
    	
    	private List<Map<String, String>> handledRequests = new ArrayList<>();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response) {
        	if (request.getRequestURI().equals("/beat")) {
        		Set<Entry<String, String[]>> entrySet = request.getParameterMap().entrySet();
        		Map<String, String> params = entrySet.stream().collect(toMap(entry -> entry.getKey(), this::getValueOrThrowIfMultiple));
        		handledRequests.add(params);
        	}
        }

		private String getValueOrThrowIfMultiple(Entry<String, String[]> entry) {
			if (entry.getValue().length > 1) {
				throw new IllegalArgumentException("Multi value parameter received");
			}
			return entry.getValue()[0];
		}

		public List<Map<String, String>> getHandledRequests() {
			return handledRequests;
		}
        
        
    }
}
